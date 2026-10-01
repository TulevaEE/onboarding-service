package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.GapCause.MISSING_NAV;
import static ee.tuleva.onboarding.investment.check.tracking.GapFillRun.NOTHING_TO_FILL;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  TrackingDifferenceService.class,
  TrackingDifferenceCalculator.class,
  InvestmentParameterRepository.class,
  FundNavQueryService.class,
  PublicHolidays.class,
  FeeAccrualRepository.class,
  FeeChargedToFundPolicy.class,
  SecurityDataBuilder.class,
  ConsecutiveBreachTracker.class,
  BenchmarkCheckBuilder.class,
  BenchmarkLegResolver.class,
  StaleFundReturnDetector.class,
  TrackingDifferenceServiceGapFillIT.FixedClockConfiguration.class
})
class TrackingDifferenceServiceGapFillIT {

  private static final int LOOKBACK_DAYS = 30;
  private static final LocalDate TUESDAY = LocalDate.of(2026, 4, 7);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 4, 8);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 4, 9);
  private static final LocalDate GOOD_FRIDAY = LocalDate.of(2026, 4, 3);
  private static final LocalDate EASTER_SATURDAY = LocalDate.of(2026, 4, 4);
  private static final LocalDate LAST_WORKING_DAY_THURSDAY_STAYS_IN_THE_WINDOW =
      LocalDate.of(2026, 5, 8);
  private static final LocalDate MODEL_EFFECTIVE_DATE = LocalDate.of(2026, 1, 1);
  private static final String HELD_ISIN = "IE00B4L5Y983";
  private static final BigDecimal HOLDING_VALUE = new BigDecimal("1000000.00");

  @TestConfiguration
  static class FixedClockConfiguration {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-04-10T16:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  @Autowired TrackingDifferenceService service;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Autowired NavReportRepository navReportRepository;
  @Autowired JdbcClient jdbcClient;

  @MockitoBean PositionPriceResolver positionPriceResolver;
  @MockitoBean FundValueProvider fundValueProvider;
  @MockitoBean PriorityPriceProvider priorityPriceProvider;
  @MockitoBean InstrumentReferenceService instrumentReferenceService;

  @BeforeEach
  void storeTheTrackingParameters() {
    storeParameter(TRACKING_BREACH_THRESHOLD, new BigDecimal("0.002"));
    storeParameter(TRACKING_MAX_DAILY_RETURN, new BigDecimal("0.5"));
  }

  @Test
  void namesAWorkingDayWithPositionsAndAModelButNoNavAsAGapForWantOfANav() {
    storeModelPortfolio();
    storeHolding(THURSDAY);

    assertThat(service.fillGaps(LOOKBACK_DAYS))
        .isEqualTo(namedForWantOfANav(THURSDAY, "no NAV for the check date"));
  }

  @Test
  void keepsNamingTheDateForWantOfANavWhileItsWorkingDayBeforeHasNone() {
    storeModelPortfolio();
    storeHolding(THURSDAY);
    publishNav(TUESDAY, "10.0000");
    publishNav(THURSDAY, "10.1000");

    assertThat(service.fillGaps(LOOKBACK_DAYS))
        .isEqualTo(namedForWantOfANav(THURSDAY, "no NAV for the working day before, " + WEDNESDAY));
  }

  @Test
  void aDateNamedForWantOfANavIsCheckedOnceTheNavIsPublishedAndIsNotNamedAgain() {
    storeModelPortfolio();
    storeHolding(THURSDAY);
    givenPrice(WEDNESDAY, "100.00");
    givenPrice(THURSDAY, "101.00");
    assertThat(service.fillGaps(LOOKBACK_DAYS))
        .isEqualTo(namedForWantOfANav(THURSDAY, "no NAV for the check date"));

    publishNav(WEDNESDAY, "10.0000");
    publishNav(THURSDAY, "10.1000");

    assertThat(service.fillGaps(LOOKBACK_DAYS))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new GapFillRun(List.of(passingModelPortfolioCheckOn(THURSDAY)), List.of(), Map.of()));
    assertThat(service.fillGaps(LOOKBACK_DAYS)).isEqualTo(NOTHING_TO_FILL);
  }

  @Test
  void namesNothingForAPositionDateOnAPublicHolidayOrAWeekendSinceNoNavIsEverCalculatedForIt() {
    storeModelPortfolio();
    storeHolding(GOOD_FRIDAY);
    storeHolding(EASTER_SATURDAY);

    assertThat(service.fillGaps(LOOKBACK_DAYS)).isEqualTo(NOTHING_TO_FILL);
  }

  @Test
  void namesNothingForAFundsFirstNavDaySinceNoNavBeforeItWillEverBeCalculated() {
    storeModelPortfolio();
    storeHolding(THURSDAY);
    publishNav(THURSDAY, "10.1000");

    assertThat(service.fillGaps(LOOKBACK_DAYS)).isEqualTo(NOTHING_TO_FILL);
  }

  @Test
  void namesNothingForAFundWithNoModelPortfolioEvenWhenItsNavIsMissing() {
    storeHolding(THURSDAY);

    assertThat(service.fillGaps(LOOKBACK_DAYS)).isEqualTo(NOTHING_TO_FILL);
  }

  @Test
  void namesNothingForAFundWithNoModelPortfolioSinceNothingWouldEverFillIt() {
    storeHolding(THURSDAY);
    publishNav(WEDNESDAY, "10.0000");
    publishNav(THURSDAY, "10.1000");

    assertThat(service.fillGaps(LOOKBACK_DAYS)).isEqualTo(NOTHING_TO_FILL);
  }

  private static GapFillRun namedForWantOfANav(LocalDate checkDate, String reason) {
    return new GapFillRun(
        List.of(),
        List.of(
            new GapFailure(
                checkDate,
                "fund=TUK75, " + reason,
                MISSING_NAV,
                1,
                LAST_WORKING_DAY_THURSDAY_STAYS_IN_THE_WINDOW)),
        Map.of());
  }

  private static TrackingDifferenceResult passingModelPortfolioCheckOn(LocalDate checkDate) {
    var onePercent = new BigDecimal("0.01");
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(checkDate)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(ZERO)
        .fundReturn(onePercent)
        .benchmarkReturn(onePercent)
        .breach(false)
        .consecutiveBreachDays(0)
        .consecutiveNetTd(ZERO)
        .securityAttributions(
            List.of(
                new SecurityAttribution(
                    HELD_ISIN, BigDecimal.ONE, BigDecimal.ONE, ZERO, onePercent, null, ZERO)))
        .cashDrag(ZERO)
        .feeDrag(ZERO)
        .residual(ZERO)
        .build();
  }

  private void storeModelPortfolio() {
    modelPortfolioAllocationRepository.save(
        ModelPortfolioAllocation.builder()
            .fund(TUK75)
            .effectiveDate(MODEL_EFFECTIVE_DATE)
            .isin(HELD_ISIN)
            .weight(BigDecimal.ONE)
            .build());
  }

  private void storeHolding(LocalDate navDate) {
    fundPositionRepository.save(
        FundPosition.builder()
            .fund(TUK75)
            .navDate(navDate)
            .accountType(SECURITY)
            .accountName("Synthetic equity ETF")
            .accountId(HELD_ISIN)
            .quantity(new BigDecimal("10000"))
            .marketValue(HOLDING_VALUE)
            .build());
  }

  private void publishNav(LocalDate navDate, String navPerUnit) {
    navReportRepository.save(
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(TUK75.getCode())
            .accountType("NAV")
            .accountName("Net asset value per unit")
            .marketPrice(new BigDecimal(navPerUnit))
            .calculationId(UUID.randomUUID())
            .publishedAt(navDate.plusDays(1).atTime(16, 0).toInstant(UTC))
            .build());
  }

  private void givenPrice(LocalDate priceDate, String price) {
    given(positionPriceResolver.resolve(eq(HELD_ISIN), eq(priceDate), any(Instant.class)))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal(price))
                    .priceDate(priceDate)
                    .validationStatus(OK)
                    .build()));
  }

  private void storeParameter(InvestmentParameter parameter, BigDecimal numericValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter (effective_date, parameter_name, numeric_value)
            VALUES (:effectiveDate, :name, :numericValue)
            """)
        .param("effectiveDate", MODEL_EFFECTIVE_DATE)
        .param("name", parameter.name())
        .param("numericValue", numericValue)
        .update();
  }
}
