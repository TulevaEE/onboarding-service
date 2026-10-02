package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED_NET;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.TIERED_VOLUME;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({
  InstrumentOcfServiceIT.Json.class,
  InstrumentOcfService.class,
  InstrumentFeeAgreementRepository.class,
  InstrumentFeeRateRepository.class,
  MonthVolumeReader.class,
  RebateCalculator.class,
  FundNavQueryService.class,
  PublicHolidays.class
})
class InstrumentOcfServiceIT {

  private static final String ISIN = "ZZ0000000001";
  private static final String SECOND_ISIN = "ZZ0000000002";
  private static final BigDecimal PUBLISHED_OCF = new BigDecimal("0.0007");
  private static final String NO_VOLUME_FOR_BOTH_FUNDS =
      "the month's volume cannot be measured: a fund named in the agreement has no published NAV"
          + " for the day, funds=[TUK75, TUV100]";
  private static final RecursiveComparisonConfiguration IGNORING_ID_AMOUNTS_BY_VALUE =
      RecursiveComparisonConfiguration.builder()
          .withIgnoredFields("id")
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .build();
  private static final YearMonth APRIL = YearMonth.of(2026, 4);
  private static final LocalDate APRIL_28 = LocalDate.of(2026, 4, 28);
  private static final LocalDate APRIL_29 = LocalDate.of(2026, 4, 29);
  private static final LocalDate APRIL_30 = LocalDate.of(2026, 4, 30);
  private static final YearMonth MAY = YearMonth.of(2026, 5);
  private static final LocalDate FRIDAY_MAY_29 = LocalDate.of(2026, 5, 29);
  private static final String GATED_FIXED_NET =
      "{\"net\":\"0.00050000\",\"minimum\":\"50000000.00\",\"minimumCurrency\":\"EUR\","
          + "\"funds\":[\"TUK75\",\"TUV100\"]}";

  @TestConfiguration
  static class Json {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @MockitoBean private FundValueProvider fundValueProvider;

  @Autowired private InstrumentOcfService service;
  @Autowired private NavReportRepository navReportRepository;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void aSyntheticInstrumentAndNoExchangeRateUnlessATestGivesOne() {
    inTheInstrumentReference(ISIN);
    given(fundValueProvider.getLatestValue(any(), any())).willReturn(Optional.empty());
  }

  @Test
  void aGatedFixedNetAgreementGivesTheAgreedNetOnceTheHoldingIsAboveTheGate() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);
    heldOn(APRIL_30, TUK75, "30000000.00");
    heldOn(APRIL_30, TUV100, "25000000.00");

    var rate = service.resolve(APRIL).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            new InstrumentRate(
                0,
                ISIN,
                APRIL,
                PUBLISHED_OCF,
                new BigDecimal("0.0005"),
                AGREEMENT,
                null,
                FIXED_NET));
    assertThat(storedVolume()).isEqualByComparingTo("55000000.00");
  }

  @Test
  void belowTheGateTheFundPaysThePublishedOcf() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);
    heldOn(APRIL_30, TUK75, "20000000.00");
    heldOn(APRIL_30, TUV100, "20000000.00");

    var rate = service.resolve(APRIL).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            new InstrumentRate(
                0, ISIN, APRIL, PUBLISHED_OCF, PUBLISHED_OCF, AGREEMENT, null, FIXED_NET));
  }

  @Test
  void theMonthsVolumeIsTheLastPublishedDayTheInstrumentWasStillHeld() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);
    heldOn(APRIL_28, TUK75, "60000000.00");
    heldOn(APRIL_29, TUK75, "0.00");
    heldOn(APRIL_30, TUK75, "0.00");
    heldOn(APRIL_28, TUV100, "0.00");
    heldOn(APRIL_29, TUV100, "0.00");
    heldOn(APRIL_30, TUV100, "0.00");

    service.resolve(APRIL);

    assertThat(storedVolume()).isEqualByComparingTo("60000000.00");
    assertThat(storedVolumeNavDate()).isEqualTo(APRIL_28);
  }

  @Test
  void aUsdTierWithoutAnExchangeRateFallsBackToThePublishedOcfAndSaysWhy() {
    givenAnAgreement(
        "TIERED_VOLUME",
        "{\"threshold\":\"110000000\",\"currency\":\"USD\",\"rateBelow\":\"0.0002\","
            + "\"rateAbove\":\"0.0001\",\"funds\":[\"TUK75\"]}");
    heldOn(APRIL_30, TUK75, "80000000.00");

    var rate = service.resolve(APRIL).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            new InstrumentRate(
                0,
                ISIN,
                APRIL,
                PUBLISHED_OCF,
                PUBLISHED_OCF,
                PUBLISHED_FALLBACK,
                "the tiered agreement's USD threshold needs the EUR/USD rate",
                TIERED_VOLUME));
  }

  @Test
  void aMonthFailsUntilEachNamedFundHasPublishedTheNavOfTheMonthsLastWorkingDay() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);
    heldOn(APRIL_29, TUK75, "30000000.00");
    heldOn(APRIL_30, TUK75, "30000000.00");
    heldOn(APRIL_29, TUV100, "25000000.00");

    assertThatThrownBy(() -> service.resolve(APRIL))
        .isInstanceOf(MonthNavNotYetPublishedException.class);
    assertThat(storedRates()).isZero();
  }

  @Test
  void aMonthEndingOnAWeekendIsMeasuredOnTheNavOfItsLastWorkingDay() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);
    heldOn(FRIDAY_MAY_29, TUK75, "30000000.00");
    heldOn(FRIDAY_MAY_29, TUV100, "25000000.00");

    var rate = service.resolve(MAY).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            new InstrumentRate(
                0, ISIN, MAY, PUBLISHED_OCF, new BigDecimal("0.0005"), AGREEMENT, null, FIXED_NET));
    assertThat(storedVolumeNavDate()).isEqualTo(FRIDAY_MAY_29);
  }

  @Test
  void aGatedAgreementInAMonthWithNoPublishedNavFallsBackToThePublishedOcfAndSaysWhy() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);

    var rate = service.resolve(APRIL).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(fallback(NO_VOLUME_FOR_BOTH_FUNDS, FIXED_NET));
  }

  @Test
  void aNamedFundWithNoPublishedCalculationOnTheDayFallsBackRatherThanLeavingItsHoldingOut() {
    givenAnAgreement("FIXED_NET", GATED_FIXED_NET);
    heldOn(APRIL_30, TUK75, "60000000.00");

    var rate = service.resolve(APRIL).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(fallback(NO_VOLUME_FOR_BOTH_FUNDS, FIXED_NET));
  }

  @Test
  void aRebateOutsideZeroAndThePublishedOcfFallsBackInsteadOfStoppingTheRun() {
    givenAnAgreement("FIXED", "{\"rate\":\"-0.00020000\"}");

    var rate = service.resolve(APRIL).getFirst();

    assertThat(rate)
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            fallback(
                "the agreement gives a rebate outside zero and the published OCF:"
                    + " rebate=-0.00020000, publishedOcf=0.00070000",
                FIXED));
  }

  @Test
  void anAgreementWhoseTermsAreJsonNullFallsBackWithoutStoppingTheOthers() {
    givenAnAgreement("FIXED", "null");
    inTheInstrumentReference(SECOND_ISIN);
    givenAnAgreement(SECOND_ISIN, "FIXED", "{\"rate\":\"0.00010000\"}");

    var rates = service.resolve(APRIL);

    assertThat(rates)
        .usingRecursiveFieldByFieldElementComparator(IGNORING_ID_AMOUNTS_BY_VALUE)
        .containsExactly(
            fallback("the agreement could not be applied: IllegalArgumentException", FIXED),
            new InstrumentRate(
                0,
                SECOND_ISIN,
                APRIL,
                PUBLISHED_OCF,
                new BigDecimal("0.0006"),
                AGREEMENT,
                null,
                FIXED));
  }

  @Test
  void aReaderStoresNoRateSoOnlyTheRateJobSettlesAMonth() {
    givenAnAgreement("NONE", "{}");

    assertThat(service.ratesFor(APRIL)).isEmpty();
    assertThat(storedRates()).isZero();
    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isFalse();
  }

  @Test
  void anAgreementEnteredAfterTheMonthWasResolvedLeavesItUnresolvedUntilTheJobResolvesItAgain() {
    givenAnAgreement("NONE", "{}");
    service.resolve(APRIL);
    inTheInstrumentReference(SECOND_ISIN);
    givenAnAgreement(SECOND_ISIN, "NONE", "{}");

    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isFalse();

    service.resolve(APRIL);

    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isTrue();
    assertThat(service.ratesFor(APRIL)).containsOnlyKeys(ISIN, SECOND_ISIN);
  }

  @Test
  void aMonthIsResolvedOnlyByRatesStoredAfterItClosed() {
    givenAnAgreement("NONE", "{}");
    service.resolve(APRIL);
    jdbcClient
        .sql("UPDATE investment_instrument_fee_rate SET created_at = :midApril")
        .param("midApril", Timestamp.from(APRIL.atDay(15).atStartOfDay().toInstant(UTC)))
        .update();

    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isFalse();

    service.resolve(APRIL);

    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isTrue();
  }

  @Test
  void aMonthAlreadyResolvedIsReadRatherThanComputedAgain() {
    givenAnAgreement("NONE", "{}");
    var resolved = service.resolve(APRIL);

    var read = service.ratesFor(APRIL).get(ISIN);

    assertThat(read.id()).isEqualTo(resolved.getFirst().id());
    assertThat(storedRates()).isEqualTo(1);
  }

  @Test
  void aDeliberateRerunAppendsAndTheNewestRowIsTheOneRead() {
    givenAnAgreement("NONE", "{}");
    service.resolve(APRIL);
    var rerun = service.resolve(APRIL);

    assertThat(storedRates()).isEqualTo(2);
    assertThat(service.ratesFor(APRIL).get(ISIN).id()).isEqualTo(rerun.getFirst().id());
  }

  @Test
  void anAgreementReplacedAfterItsMonthWasResolvedReopensTheMonthUntilTheJobResolvesItAgain() {
    givenAnAgreement("NONE", "{}");
    service.resolve(APRIL);
    jdbcClient
        .sql("UPDATE investment_instrument_fee SET valid_to = valid_from WHERE isin = :isin")
        .param("isin", ISIN)
        .update();
    givenAnAgreement(ISIN, "FIXED", "{\"rate\":\"0.00010000\"}", LocalDate.of(2026, 1, 2));

    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isFalse();

    service.resolve(APRIL);

    assertThat(service.hasRatesResolvedAfterItClosed(APRIL)).isTrue();
    assertThat(service.ratesFor(APRIL).get(ISIN))
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            new InstrumentRate(
                0, ISIN, APRIL, PUBLISHED_OCF, new BigDecimal("0.0006"), AGREEMENT, null, FIXED));
  }

  @Test
  void aRateIsNotReadOnceItsAgreementNoLongerCoversTheMonthEnd() {
    givenAnAgreement("NONE", "{}");
    service.resolve(APRIL);
    jdbcClient
        .sql("UPDATE investment_instrument_fee SET valid_to = DATE '2026-04-15' WHERE isin = :isin")
        .param("isin", ISIN)
        .update();

    assertThat(service.ratesFor(APRIL)).isEmpty();
  }

  @Test
  void aLaterAgreementBeginningInTheMonthBesideAnOpenOneReplacesItsRateOnlyOnceResolvedAgain() {
    givenAnAgreement("NONE", "{}");
    service.resolve(APRIL);
    givenAnAgreement(ISIN, "FIXED", "{\"rate\":\"0.00010000\"}", APRIL.atDay(15));

    assertThat(service.ratesFor(APRIL)).doesNotContainKey(ISIN);

    service.resolve(APRIL);

    assertThat(service.ratesFor(APRIL).get(ISIN))
        .usingRecursiveComparison(IGNORING_ID_AMOUNTS_BY_VALUE)
        .isEqualTo(
            new InstrumentRate(
                0, ISIN, APRIL, PUBLISHED_OCF, new BigDecimal("0.0006"), AGREEMENT, null, FIXED));
  }

  @Test
  void aMonthWhoseInputsCannotBeReadFailsRatherThanStoringAFallbackThatWouldSettleIt() {
    givenAnAgreement(
        "TIERED_VOLUME",
        "{\"threshold\":\"110000000\",\"currency\":\"USD\",\"rateBelow\":\"0.0002\","
            + "\"rateAbove\":\"0.0001\",\"funds\":[\"TUK75\"]}");
    heldOn(APRIL_30, TUK75, "80000000.00");
    given(fundValueProvider.getLatestValue(any(), any()))
        .willThrow(new DataAccessResourceFailureException("synthetic outage"));

    assertThatThrownBy(() -> service.resolve(APRIL))
        .isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(storedRates()).isZero();
  }

  private void inTheInstrumentReference(String isin) {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", isin)
        .update();
  }

  private void givenAnAgreement(String rebateKind, String rebateTerms) {
    givenAnAgreement(ISIN, rebateKind, rebateTerms);
  }

  private void givenAnAgreement(String isin, String rebateKind, String rebateTerms) {
    givenAnAgreement(isin, rebateKind, rebateTerms, LocalDate.of(2026, 1, 1));
  }

  private void givenAnAgreement(
      String isin, String rebateKind, String rebateTerms, LocalDate validFrom) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, valid_from)
            VALUES (:isin, 0.00070000, :rebateKind, :rebateTerms, :validFrom)
            """)
        .param("isin", isin)
        .param("rebateKind", rebateKind)
        .param("rebateTerms", rebateTerms)
        .param("validFrom", validFrom)
        .update();
  }

  private void heldOn(LocalDate navDate, TulevaFund fund, String marketValue) {
    navReportRepository.save(
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(fund.getCode())
            .accountType("SECURITY")
            .accountName("Synthetic holding")
            .accountId(ISIN)
            .marketValue(new BigDecimal(marketValue))
            .calculationId(UUID.randomUUID())
            .publishedAt(navDate.atTime(16, 0).toInstant(UTC))
            .build());
  }

  private static InstrumentRate fallback(String reason, RebateKind rebateKind) {
    return new InstrumentRate(
        0, ISIN, APRIL, PUBLISHED_OCF, PUBLISHED_OCF, PUBLISHED_FALLBACK, reason, rebateKind);
  }

  private long storedRates() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM investment_instrument_fee_rate")
        .query(Long.class)
        .single();
  }

  private BigDecimal storedVolume() {
    return jdbcClient
        .sql("SELECT volume_eur FROM investment_instrument_fee_rate")
        .query(BigDecimal.class)
        .single();
  }

  private LocalDate storedVolumeNavDate() {
    return jdbcClient
        .sql("SELECT volume_nav_date FROM investment_instrument_fee_rate")
        .query(LocalDate.class)
        .single();
  }
}
