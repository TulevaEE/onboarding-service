package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_AVERAGE;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_END;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.STANDARD_AFTER_AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.fees.rate.AgreementPeriod;
import ee.tuleva.onboarding.investment.fees.rate.ApplicableRate;
import ee.tuleva.onboarding.investment.fees.rate.DailyRates;
import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnderlyingWeightsTest {

  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final LocalDate JUNE_10 = LocalDate.of(2026, 6, 10);
  private static final LocalDate JUNE_15 = LocalDate.of(2026, 6, 15);
  private static final LocalDate JUNE_20 = LocalDate.of(2026, 6, 20);
  private static final LocalDate JUNE_29 = LocalDate.of(2026, 6, 29);
  private static final LocalDate JUNE_30 = LocalDate.of(2026, 6, 30);
  private static final String KEPT = "ZZ0000000001";
  private static final String SOLD_MID_MONTH = "ZZ0000000002";
  private static final long KEPT_AGREEMENT = 11L;
  private static final long SOLD_AGREEMENT = 12L;
  private static final BigDecimal SISEKORD_4_DEVIATION_THRESHOLD = new BigDecimal("0.015");
  private static final BigDecimal KEPT_NET = new BigDecimal("0.0010");
  private static final BigDecimal SOLD_PUBLISHED = new BigDecimal("0.0020");
  private static final BigDecimal SOLD_NET = new BigDecimal("0.0005");

  @Mock private FundNavQueryService fundNavQueryService;

  private UnderlyingWeights underlyingWeights;

  @BeforeEach
  void setUp() {
    underlyingWeights = new UnderlyingWeights(fundNavQueryService, SISEKORD_4_DEVIATION_THRESHOLD);
  }

  private static NavCalculation calculation(Map<String, String> securities, String aum) {
    var lines =
        new ArrayList<NavAccountLine>(
            securities.entrySet().stream()
                .map(
                    security ->
                        new NavAccountLine(
                            "SECURITY",
                            security.getKey(),
                            security.getKey(),
                            null,
                            null,
                            new BigDecimal(security.getValue())))
                .toList());
    lines.add(new NavAccountLine("UNITS", "UNITS", null, null, null, new BigDecimal(aum)));
    return new NavCalculation(UUID.randomUUID(), Instant.EPOCH, List.copyOf(lines));
  }

  private void publishedInJune(Map<LocalDate, NavCalculation> calculations) {
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                TKF100.getCode(), JUNE.atDay(1), JUNE.atEndOfMonth()))
        .willReturn(calculations.keySet().stream().sorted().toList());
    calculations.forEach(
        (date, calculation) ->
            given(fundNavQueryService.findPublishedCalculation(TKF100.getCode(), date))
                .willReturn(Optional.of(calculation)));
  }

  private static InstrumentRate rate(long id, String isin, BigDecimal published, BigDecimal net) {
    return new InstrumentRate(id, isin, JUNE, published, net, AGREEMENT, null, FIXED);
  }

  private static DailyRates rates(@org.jspecify.annotations.Nullable LocalDate soldAgreementEnds) {
    return new DailyRates(
        JUNE,
        List.of(
            new AgreementPeriod(KEPT_AGREEMENT, KEPT, KEPT_NET, LocalDate.of(2026, 1, 1), null),
            new AgreementPeriod(
                SOLD_AGREEMENT,
                SOLD_MID_MONTH,
                SOLD_PUBLISHED,
                LocalDate.of(2026, 1, 1),
                soldAgreementEnds)),
        Map.of(
            KEPT_AGREEMENT, rate(101L, KEPT, KEPT_NET, KEPT_NET),
            SOLD_AGREEMENT, rate(102L, SOLD_MID_MONTH, SOLD_PUBLISHED, SOLD_NET)));
  }

  private static ApplicableRate keptRate() {
    return new ApplicableRate(KEPT, KEPT_AGREEMENT, 101L, KEPT_NET, KEPT_NET, AGREEMENT, null);
  }

  private static ApplicableRate soldAgreementRate() {
    return new ApplicableRate(
        SOLD_MID_MONTH, SOLD_AGREEMENT, 102L, SOLD_PUBLISHED, SOLD_NET, AGREEMENT, null);
  }

  @Test
  void aRepresentativeMonthEndKeepsTheMonthEndWeights() {
    var monthEnd = calculation(Map.of(KEPT, "970"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10, calculation(Map.of(KEPT, "975"), "1000"),
            JUNE_20, calculation(Map.of(KEPT, "965"), "1000"),
            JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_END);
    assertThat(weights.holdings(rates(null)))
        .containsExactly(new OcfHolding(new BigDecimal("0.970000000000"), keptRate()));
    assertThat(weights.investedRatioMonthEnd()).isEqualByComparingTo("0.97");
    assertThat(weights.investedRatioMonthAverage()).isEqualByComparingTo("0.97");
  }

  @Test
  void anUnrepresentativeMonthEndWeighsEachUnderlyingFundByItsMonthAverageShare() {
    var monthEnd = calculation(Map.of(KEPT, "940"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10, calculation(Map.of(KEPT, "900", SOLD_MID_MONTH, "90"), "1000"),
            JUNE_20, calculation(Map.of(KEPT, "980"), "1000"),
            JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_AVERAGE);
    assertThat(weights.holdings(rates(null)))
        .containsExactly(
            new OcfHolding(new BigDecimal("0.940000000000"), keptRate()),
            new OcfHolding(new BigDecimal("0.030000000000"), soldAgreementRate()));
    assertThat(weights.investedRatioMonthEnd()).isEqualByComparingTo("0.94");
    assertThat(weights.investedRatioMonthAverage()).isEqualByComparingTo("0.97");
  }

  @Test
  void anAgreementEndingMidMonthGivesItsRateWhileInForceAndThePublishedOcfAfterIt() {
    var monthEnd = calculation(Map.of(KEPT, "940"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10, calculation(Map.of(KEPT, "900", SOLD_MID_MONTH, "90"), "1000"),
            JUNE_20, calculation(Map.of(KEPT, "900", SOLD_MID_MONTH, "60"), "1000"),
            JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_AVERAGE);
    assertThat(weights.holdings(rates(JUNE_15)))
        .containsExactly(
            new OcfHolding(new BigDecimal("0.913333333333"), keptRate()),
            new OcfHolding(new BigDecimal("0.030000000000"), soldAgreementRate()),
            new OcfHolding(
                new BigDecimal("0.020000000000"),
                new ApplicableRate(
                    SOLD_MID_MONTH,
                    SOLD_AGREEMENT,
                    null,
                    SOLD_PUBLISHED,
                    SOLD_PUBLISHED,
                    STANDARD_AFTER_AGREEMENT,
                    ("its agreement ended on 2026-06-15, so the published OCF applies after it; enter the new"
                        + " agreement if there is one"))));
  }

  @Test
  void theMonthEndWeightsTakeTheRateInForceOnTheCalendarMonthEndRatherThanOnTheLastNavDay() {
    var monthEnd = calculation(Map.of(KEPT, "940", SOLD_MID_MONTH, "30"), "1000");
    publishedInJune(Map.of(JUNE_29, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_END);
    assertThat(weights.holdings(rates(JUNE_29)))
        .containsExactly(
            new OcfHolding(new BigDecimal("0.940000000000"), keptRate()),
            new OcfHolding(
                new BigDecimal("0.030000000000"),
                new ApplicableRate(
                    SOLD_MID_MONTH,
                    SOLD_AGREEMENT,
                    null,
                    SOLD_PUBLISHED,
                    SOLD_PUBLISHED,
                    STANDARD_AFTER_AGREEMENT,
                    "its agreement ended on 2026-06-29, so the published OCF applies after it;"
                        + " enter the new agreement if there is one")));
  }

  @Test
  void aHoldingBoughtBeforeItsAgreementBeganStaysUnratedUntilTheAgreementCoversThePurchase() {
    var monthEnd = calculation(Map.of(KEPT, "900", SOLD_MID_MONTH, "50"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10,
            calculation(Map.of(KEPT, "900", SOLD_MID_MONTH, "90"), "1000"),
            JUNE_30,
            monthEnd));
    var agreementFromJune15 =
        new DailyRates(
            JUNE,
            List.of(
                new AgreementPeriod(KEPT_AGREEMENT, KEPT, KEPT_NET, LocalDate.of(2026, 1, 1), null),
                new AgreementPeriod(SOLD_AGREEMENT, SOLD_MID_MONTH, SOLD_PUBLISHED, JUNE_15, null)),
            Map.of(
                KEPT_AGREEMENT, rate(101L, KEPT, KEPT_NET, KEPT_NET),
                SOLD_AGREEMENT, rate(102L, SOLD_MID_MONTH, SOLD_PUBLISHED, SOLD_NET)));
    var backdatedToJune1 =
        new DailyRates(
            JUNE,
            List.of(
                new AgreementPeriod(KEPT_AGREEMENT, KEPT, KEPT_NET, LocalDate.of(2026, 1, 1), null),
                new AgreementPeriod(
                    SOLD_AGREEMENT, SOLD_MID_MONTH, SOLD_PUBLISHED, JUNE.atDay(1), null)),
            Map.of(
                KEPT_AGREEMENT, rate(101L, KEPT, KEPT_NET, KEPT_NET),
                SOLD_AGREEMENT, rate(102L, SOLD_MID_MONTH, SOLD_PUBLISHED, SOLD_NET)));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_AVERAGE);
    assertThat(weights.unratedIsins(agreementFromJune15)).containsExactly(SOLD_MID_MONTH);
    assertThat(weights.unratedIsins(backdatedToJune1)).isEmpty();
  }

  @Test
  void anUnderlyingFundWithNoAgreementAtAllIsUnrated() {
    var monthEnd = calculation(Map.of(KEPT, "940", "ZZ0000000009", "10"), "1000");
    publishedInJune(Map.of(JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.unratedIsins(rates(null))).containsExactly("ZZ0000000009");
  }

  @Test
  void aSecurityLineWorthNothingOnSomeDayDoesNotEnterTheMonthAverageNorNeedARate() {
    var monthEnd = calculation(Map.of(KEPT, "940"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10, calculation(Map.of(KEPT, "990", "ZZ0000000009", "0"), "1000"),
            JUNE_20, calculation(Map.of(KEPT, "980"), "1000"),
            JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_AVERAGE);
    assertThat(weights.unratedIsins(rates(null))).isEmpty();
    assertThat(weights.holdings(rates(null)))
        .extracting(holding -> holding.rate().isin())
        .containsExactly(KEPT);
  }

  @Test
  void aDeviationExactlyAtTheThresholdKeepsTheMonthEndWeights() {
    var monthEnd = calculation(Map.of(KEPT, "955"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10, calculation(Map.of(KEPT, "985"), "1000"),
            JUNE_20, calculation(Map.of(KEPT, "970"), "1000"),
            JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_END);
    assertThat(weights.investedRatioMonthAverage()).isEqualByComparingTo("0.97");
  }

  @Test
  void aMonthWithNoPublishedNavKeepsTheWeightsOfTheNavItWasGivenAtTheMonthEndAndHasNoAverage() {
    var earlierNav = calculation(Map.of(KEPT, "960"), "1000");
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                TKF100.getCode(), JUNE.atDay(1), JUNE.atEndOfMonth()))
        .willReturn(List.of());

    var weights = underlyingWeights.of(TKF100, JUNE, earlierNav);

    assertThat(weights.basis()).isEqualTo(MONTH_END);
    assertThat(weights.days())
        .containsExactly(
            new UnderlyingWeights.DayWeights(
                JUNE_30, new TreeMap<>(Map.of(KEPT, new BigDecimal("0.960000000000")))));
    assertThat(weights.investedRatioMonthEnd()).isEqualByComparingTo("0.96");
    assertThat(weights.investedRatioMonthAverage()).isNull();
  }
}
