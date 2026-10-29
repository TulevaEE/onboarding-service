package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeRateSource.FIXED;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_AVERAGE;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.DepotRateResolver;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.rate.InstrumentOcfService;
import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.investment.fees.rate.RebateKind;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({
  OcfCalculationServiceIT.Json.class,
  OcfCalculationService.class,
  OcfSnapshotRepository.class,
  HistoricalTransactionFeeRepository.class,
  OcfJson.class,
  MonthEndNavCheck.class,
  UnderlyingWeights.class,
  FundNavQueryService.class,
  PublicHolidays.class
})
class OcfCalculationServiceIT {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final LocalDate TUESDAY_SEPTEMBER_29 = LocalDate.of(2026, 9, 29);
  private static final LocalDate WEDNESDAY_SEPTEMBER_30 = LocalDate.of(2026, 9, 30);
  private static final YearMonth MAY = YearMonth.of(2026, 5);
  private static final LocalDate FRIDAY_MAY_29 = LocalDate.of(2026, 5, 29);
  private static final String PUBLISHED_IN = "KID 2026";
  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final String SYNTHETIC_ISIN = "ZZ0000000001";

  @TestConfiguration
  static class Json {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @MockitoBean private FeeRateRepository feeRateRepository;
  @MockitoBean private FeeChargedToFundPolicy feeChargedToFundPolicy;
  @MockitoBean private DepotRateResolver depotRateResolver;
  @MockitoBean private InstrumentOcfService instrumentOcfService;
  @MockitoBean private OcfNotifier ocfNotifier;

  @Autowired private OcfCalculationService service;
  @Autowired private OcfSnapshotRepository snapshotRepository;
  @Autowired private NavReportRepository navReportRepository;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void aManagementFeeRateSoTheNavIsTheOnlyInputThatCanLeaveAGap() {
    given(feeRateRepository.findValidRate(any(), any(), any()))
        .willReturn(
            Optional.of(
                new FeeRate(
                    1L,
                    TUK75,
                    MANAGEMENT,
                    new BigDecimal("0.0020"),
                    FIXED,
                    LocalDate.of(2026, 1, 1),
                    null)));
  }

  @Test
  void aMonthEndNavNotYetPublishedStoresTheSnapshotIncompleteAndPublishingRefusesIt() {
    publishedNav(TUESDAY_SEPTEMBER_29);
    unpublishedNav(WEDNESDAY_SEPTEMBER_30);

    service.calculateOcf(TUK75, SEPTEMBER);

    var stored = storedSnapshot(SEPTEMBER);
    assertThat(stored.complete()).isFalse();
    assertThat(stored.checks()).contains("\"gaps\":[\"MONTH_END_NAV_NOT_PUBLISHED\"]");
    assertThat(stored.audit().navDate()).isEqualTo(TUESDAY_SEPTEMBER_29);
    assertThatThrownBy(() -> service.publish(TUK75, SEPTEMBER, PUBLISHED_IN))
        .isInstanceOf(IncompleteOcfSnapshotException.class);
  }

  @Test
  void publishingTheMonthEndNavAndRerunningClearsTheGapSoTheSnapshotPublishes() {
    publishedNav(TUESDAY_SEPTEMBER_29);
    var monthEndCalculation = unpublishedNav(WEDNESDAY_SEPTEMBER_30);
    service.calculateOcf(TUK75, SEPTEMBER);
    navReportRepository.markAsPublished(monthEndCalculation);

    service.calculateOcf(TUK75, SEPTEMBER);

    var stored = storedSnapshot(SEPTEMBER);
    assertThat(stored.complete()).isTrue();
    assertThat(stored.checks()).contains("\"gaps\":[]");
    assertThat(stored.audit().navDate()).isEqualTo(WEDNESDAY_SEPTEMBER_30);
    assertThat(service.publish(TUK75, SEPTEMBER, PUBLISHED_IN)).isTrue();
  }

  @Test
  void aMonthEndingOnAWeekendIsCompleteOnTheNavOfItsLastWorkingDay() {
    publishedNav(FRIDAY_MAY_29);

    service.calculateOcf(TUK75, MAY);

    var stored = storedSnapshot(MAY);
    assertThat(stored.complete()).isTrue();
    assertThat(stored.checks()).contains("\"gaps\":[]");
    assertThat(stored.audit().navDate()).isEqualTo(FRIDAY_MAY_29);
  }

  @Test
  void aMonthWhoseMonthEndCashSpikedWeighsTheUnderlyingFundsByTheMonthAverageAndRecordsWhy() {
    var rate = givenAStoredJuneRate(new BigDecimal("0.0012"), new BigDecimal("0.0010"));
    given(instrumentOcfService.ratesFor(JUNE)).willReturn(Map.of(SYNTHETIC_ISIN, rate));
    publishedNavWithSecurities(LocalDate.of(2026, 6, 10), "990000.00");
    publishedNavWithSecurities(LocalDate.of(2026, 6, 19), "980000.00");
    publishedNavWithSecurities(LocalDate.of(2026, 6, 30), "940000.00");

    service.calculateOcf(TUK75, JUNE);

    var stored = storedSnapshot(JUNE);
    assertThat(stored.complete()).isTrue();
    assertThat(stored.audit().underlyingWeightBasis()).isEqualTo(MONTH_AVERAGE);
    assertThat(stored.audit().investedRatioMonthEnd()).isEqualByComparingTo("0.94");
    assertThat(stored.audit().investedRatioMonthAverage()).isEqualByComparingTo("0.97");
    assertThat(stored.underlyingFundCost()).isEqualByComparingTo("0.00097");
  }

  private InstrumentRate givenAStoredJuneRate(BigDecimal publishedOcf, BigDecimal netOcf) {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", SYNTHETIC_ISIN)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, valid_from)
            VALUES (:isin, :publishedOcf, 'FIXED', '{}', DATE '2026-01-01')
            """)
        .param("isin", SYNTHETIC_ISIN)
        .param("publishedOcf", publishedOcf)
        .update();
    var agreementId =
        jdbcClient
            .sql("SELECT id FROM investment_instrument_fee WHERE isin = :isin")
            .param("isin", SYNTHETIC_ISIN)
            .query(Long.class)
            .single();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_rate
              (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
               net_ocf, rate_basis, rebate_kind, instrument_fee_id)
            VALUES (:isin, DATE '2026-06-01', DATE '2026-06-30', :published, :rebate, 0, :net,
                    'AGREEMENT', 'FIXED', :agreementId)
            """)
        .param("isin", SYNTHETIC_ISIN)
        .param("published", publishedOcf)
        .param("rebate", publishedOcf.subtract(netOcf))
        .param("net", netOcf)
        .param("agreementId", agreementId)
        .update();
    var rateId =
        jdbcClient
            .sql("SELECT id FROM investment_instrument_fee_rate WHERE isin = :isin")
            .param("isin", SYNTHETIC_ISIN)
            .query(Long.class)
            .single();
    return new InstrumentRate(
        rateId, SYNTHETIC_ISIN, JUNE, publishedOcf, netOcf, AGREEMENT, null, RebateKind.FIXED);
  }

  private void publishedNavWithSecurities(LocalDate navDate, String securities) {
    var calculationId = UUID.randomUUID();
    var publishedAt = navDate.atTime(16, 0).toInstant(UTC);
    navReportRepository.saveAllAndFlush(
        List.of(
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(TUK75.getCode())
                .accountType("SECURITY")
                .accountName(SYNTHETIC_ISIN)
                .accountId(SYNTHETIC_ISIN)
                .marketValue(new BigDecimal(securities))
                .calculationId(calculationId)
                .publishedAt(publishedAt)
                .build(),
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(TUK75.getCode())
                .accountType("UNITS")
                .accountName("Synthetic units")
                .marketValue(new BigDecimal("1000000.00"))
                .calculationId(calculationId)
                .publishedAt(publishedAt)
                .build()));
  }

  private void publishedNav(LocalDate navDate) {
    nav(navDate, navDate.atTime(16, 0).toInstant(UTC));
  }

  private UUID unpublishedNav(LocalDate navDate) {
    return nav(navDate, null);
  }

  private UUID nav(LocalDate navDate, @Nullable Instant publishedAt) {
    var calculationId = UUID.randomUUID();
    navReportRepository.saveAllAndFlush(
        List.of(
            NavReportRow.builder()
                .navDate(navDate)
                .fundCode(TUK75.getCode())
                .accountType("UNITS")
                .accountName("Synthetic units")
                .marketValue(new BigDecimal("1000000.00"))
                .calculationId(calculationId)
                .publishedAt(publishedAt)
                .build()));
    return calculationId;
  }

  private OcfSnapshot storedSnapshot(YearMonth month) {
    return snapshotRepository.findByFundAndMonth(TUK75.getCode(), month.atDay(1)).orElseThrow();
  }
}
