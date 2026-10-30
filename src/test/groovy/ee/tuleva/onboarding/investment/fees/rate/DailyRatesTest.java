package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.STANDARD_AFTER_AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailyRatesTest {

  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final String ISIN = "ZZ0000000001";
  private static final LocalDate NEW_YEAR = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUNE_15 = LocalDate.of(2026, 6, 15);
  private static final LocalDate JUNE_16 = LocalDate.of(2026, 6, 16);
  private static final BigDecimal PUBLISHED = new BigDecimal("0.0020");
  private static final BigDecimal NET = new BigDecimal("0.0005");

  private static InstrumentRate resolved(long id, RateBasis basis, String reason) {
    return new InstrumentRate(id, ISIN, JUNE, PUBLISHED, NET, basis, reason, FIXED);
  }

  @Test
  void aDayInsideTheAgreementTakesTheAgreementsResolvedRate() {
    var rates =
        new DailyRates(
            JUNE,
            List.of(new AgreementPeriod(1L, ISIN, PUBLISHED, NEW_YEAR, JUNE_15)),
            Map.of(1L, resolved(101L, AGREEMENT, null)));

    assertThat(rates.on(ISIN, JUNE_15))
        .contains(new ApplicableRate(ISIN, 1L, 101L, PUBLISHED, NET, AGREEMENT, null));
  }

  @Test
  void aDayAfterTheAgreementEndedTakesItsPublishedOcf() {
    var rates =
        new DailyRates(
            JUNE,
            List.of(new AgreementPeriod(1L, ISIN, PUBLISHED, NEW_YEAR, JUNE_15)),
            Map.of(1L, resolved(101L, AGREEMENT, null)));

    var rate = rates.on(ISIN, JUNE_16).orElseThrow();

    assertThat(rate)
        .isEqualTo(
            new ApplicableRate(
                ISIN,
                1L,
                null,
                PUBLISHED,
                PUBLISHED,
                STANDARD_AFTER_AGREEMENT,
                "its agreement ended on 2026-06-15, so the published OCF applies after it"));
    assertThat(rate.fellBackToThePublishedOcf()).isTrue();
    assertThat(rate.isTheStandardRateAfterAnAgreement()).isTrue();
  }

  @Test
  void anAgreementThatEndedBeforeTheMonthStillGivesItsPublishedOcf() {
    var rates =
        new DailyRates(
            JUNE,
            List.of(new AgreementPeriod(1L, ISIN, PUBLISHED, NEW_YEAR, LocalDate.of(2026, 4, 30))),
            Map.of());

    assertThat(rates.on(ISIN, JUNE_16))
        .get()
        .extracting(ApplicableRate::basis)
        .isEqualTo(STANDARD_AFTER_AGREEMENT);
  }

  @Test
  void theNewestAgreementInForceDecidesTheDay() {
    var rates =
        new DailyRates(
            JUNE,
            List.of(
                new AgreementPeriod(1L, ISIN, PUBLISHED, NEW_YEAR, null),
                new AgreementPeriod(2L, ISIN, PUBLISHED, JUNE_16, null)),
            Map.of(
                1L, resolved(101L, AGREEMENT, null),
                2L, resolved(102L, PUBLISHED_FALLBACK, "no volume")));

    assertThat(rates.on(ISIN, JUNE_15)).get().extracting(ApplicableRate::rateId).isEqualTo(101L);
    var later = rates.on(ISIN, JUNE_16).orElseThrow();
    assertThat(later.rateId()).isEqualTo(102L);
    assertThat(later.reason()).isEqualTo("no volume");
    assertThat(later.fellBackToThePublishedOcf()).isTrue();
    assertThat(later.isTheStandardRateAfterAnAgreement()).isFalse();
  }

  @Test
  void anAgreementInForceWithoutAResolvedRateLeavesTheDayUnrated() {
    var rates =
        new DailyRates(
            JUNE, List.of(new AgreementPeriod(1L, ISIN, PUBLISHED, NEW_YEAR, null)), Map.of());

    assertThat(rates.on(ISIN, JUNE_15)).isEmpty();
  }

  @Test
  void anInstrumentWithNoAgreementIsUnrated() {
    var rates = new DailyRates(JUNE, List.of(), Map.of());

    assertThat(rates.on(ISIN, JUNE_15)).isEmpty();
  }

  @Test
  void aDayBeforeTheMonthIsReadAsTheMonthsFirstDay() {
    var rates =
        new DailyRates(
            JUNE,
            List.of(new AgreementPeriod(1L, ISIN, PUBLISHED, JUNE.atDay(1), null)),
            Map.of(1L, resolved(101L, AGREEMENT, null)));

    assertThat(rates.on(ISIN, LocalDate.of(2026, 5, 29))).isPresent();
  }
}
