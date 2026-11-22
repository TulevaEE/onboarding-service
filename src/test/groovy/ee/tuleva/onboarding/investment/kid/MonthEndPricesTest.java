package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MonthEndPricesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

  @Test
  void aClosedMonthMissesNothingEvenAfterTheSeriesStopped() {
    var stopped = new MonthEndPrices(TKF100, LocalDate.of(2026, 10, 8), TODAY);

    assertThat(stopped.missingCloseReason(YearMonth.of(2026, 9))).isEmpty();
  }

  @Test
  void aMonthAfterTheSeriesStoppedMissesItsCloseForTheSameReasonTheJobStores() {
    var stopped = new MonthEndPrices(TKF100, LocalDate.of(2026, 10, 8), TODAY);

    assertThat(stopped.missingCloseReason(YearMonth.of(2026, 10)))
        .isEqualTo(stopped.stoppedReason());
  }

  @Test
  void aMonthWhoseCloseIsLateOnACurrentSeriesMissesItsClose() {
    var current = new MonthEndPrices(TKF100, LocalDate.of(2026, 10, 29), TODAY);

    assertThat(current.stoppedReason()).isEmpty();
    assertThat(current.missingCloseReason(YearMonth.of(2026, 10))).isPresent();
  }

  @Test
  void aSeriesWithNoPricesMissesEveryClose() {
    var none = new MonthEndPrices(TKF100, null, TODAY);

    assertThat(none.missingCloseReason(YearMonth.of(2026, 1))).isEqualTo(none.stoppedReason());
    assertThat(none.stoppedReason()).isPresent();
  }
}
