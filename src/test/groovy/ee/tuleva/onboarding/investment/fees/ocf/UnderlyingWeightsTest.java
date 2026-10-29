package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_AVERAGE;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_END;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
  private static final LocalDate JUNE_20 = LocalDate.of(2026, 6, 20);
  private static final LocalDate JUNE_30 = LocalDate.of(2026, 6, 30);
  private static final String KEPT = "ZZ0000000001";
  private static final String SOLD_MID_MONTH = "ZZ0000000002";
  private static final BigDecimal SISEKORD_4_DEVIATION_THRESHOLD = new BigDecimal("0.015");

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
    assertThat(weights.byIsin())
        .containsExactlyEntriesOf(Map.of(KEPT, new BigDecimal("0.970000000000")));
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
    assertThat(weights.byIsin())
        .containsExactlyEntriesOf(
            new TreeMap<>(
                Map.of(
                    KEPT, new BigDecimal("0.940000000000"),
                    SOLD_MID_MONTH, new BigDecimal("0.030000000000"))));
    assertThat(weights.investedRatioMonthEnd()).isEqualByComparingTo("0.94");
    assertThat(weights.investedRatioMonthAverage()).isEqualByComparingTo("0.97");
  }

  @Test
  void aSecurityLineWorthNothingOnSomeDayDoesNotEnterTheMonthAverage() {
    var monthEnd = calculation(Map.of(KEPT, "940"), "1000");
    publishedInJune(
        Map.of(
            JUNE_10, calculation(Map.of(KEPT, "990", SOLD_MID_MONTH, "0"), "1000"),
            JUNE_20, calculation(Map.of(KEPT, "980"), "1000"),
            JUNE_30, monthEnd));

    var weights = underlyingWeights.of(TKF100, JUNE, monthEnd);

    assertThat(weights.basis()).isEqualTo(MONTH_AVERAGE);
    assertThat(weights.byIsin()).containsOnlyKeys(KEPT);
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
  void aMonthWithNoPublishedNavKeepsTheWeightsOfTheNavItWasGivenAndHasNoAverage() {
    var earlierNav = calculation(Map.of(KEPT, "960"), "1000");
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                TKF100.getCode(), JUNE.atDay(1), JUNE.atEndOfMonth()))
        .willReturn(List.of());

    var weights = underlyingWeights.of(TKF100, JUNE, earlierNav);

    assertThat(weights.basis()).isEqualTo(MONTH_END);
    assertThat(weights.byIsin())
        .containsExactlyEntriesOf(Map.of(KEPT, new BigDecimal("0.960000000000")));
    assertThat(weights.investedRatioMonthEnd()).isEqualByComparingTo("0.96");
    assertThat(weights.investedRatioMonthAverage()).isNull();
  }
}
