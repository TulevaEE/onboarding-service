package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.investment.series.SeriesSegment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OwnNavMonthEndsTest {

  private static final double ONGOING_CHARGES = 0.005;
  private static final SeriesSegment INDEX = new SeriesSegment("MSCI_ACWI", null);
  private static final SeriesSegment OWN_NAV_FROM_MARCH =
      new SeriesSegment(TKF100.getIsin(), LocalDate.of(2026, 3, 1));
  private static final List<MonthEndPrice> MONTH_ENDS =
      List.of(
          new MonthEndPrice(LocalDate.of(2026, 1, 31), 100.0),
          new MonthEndPrice(LocalDate.of(2026, 2, 28), 101.0),
          new MonthEndPrice(LocalDate.of(2026, 3, 31), 102.0),
          new MonthEndPrice(LocalDate.of(2026, 4, 30), 103.0));

  @Test
  void addsTheCostsBackOnlyToTheMonthsTheFundsOwnNavAlreadyPaidThem() {
    var grossed =
        OwnNavMonthEnds.of(TKF100, List.of(INDEX, OWN_NAV_FROM_MARCH))
            .grossedUp(MONTH_ENDS, ONGOING_CHARGES);

    assertThat(grossed.subList(0, 2)).isEqualTo(MONTH_ENDS.subList(0, 2));
    assertThat(grossed.get(2).price())
        .isCloseTo(102.0 / Math.pow(1 - ONGOING_CHARGES, 1 / 12.0), within(1e-12));
    assertThat(grossed.get(3).price())
        .isCloseTo(103.0 / Math.pow(1 - ONGOING_CHARGES, 2 / 12.0), within(1e-12));
    assertThat(grossed)
        .extracting(MonthEndPrice::monthEnd)
        .containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 30));
  }

  @Test
  void aWindowOfOwnNavMonthsShowsTheFundsOwnReturnOnceTheCostsAreTakenAgain() {
    var grossed =
        OwnNavMonthEnds.of(TKF100, List.of(INDEX, OWN_NAV_FROM_MARCH))
            .grossedUp(MONTH_ENDS, ONGOING_CHARGES);
    var marchToApril =
        new ScenarioWindow(
            WindowSource.FULL_LENGTH,
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 30),
            1,
            grossed.get(3).price() / grossed.get(2).price());

    assertThat(marchToApril.netFactor(ONGOING_CHARGES)).isCloseTo(103.0 / 102.0, within(1e-12));
  }

  @Test
  void leavesAnIndexOnlySeriesAsItIs() {
    var grossed = OwnNavMonthEnds.of(TKF100, List.of(INDEX)).grossedUp(MONTH_ENDS, 0.005);

    assertThat(grossed).isEqualTo(MONTH_ENDS);
  }

  @Test
  void refusesAnOwnNavSegmentThatStartsInsideAMonth() {
    var midMonth = new SeriesSegment(TKF100.getIsin(), LocalDate.of(2026, 3, 2));

    assertThatThrownBy(() -> OwnNavMonthEnds.of(TKF100, List.of(INDEX, midMonth)))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesAnOwnNavSegmentWithNoStartBecauseItsFirstPaidMonthIsUnknown() {
    var ownNavOnly = new SeriesSegment(TKF100.getIsin(), null);

    assertThatThrownBy(() -> OwnNavMonthEnds.of(TKF100, List.of(ownNavOnly)))
        .isInstanceOf(KidCalculationRefusedException.class);
  }
}
