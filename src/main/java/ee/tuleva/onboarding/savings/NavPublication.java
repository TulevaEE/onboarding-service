package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.savings.NavPublication.Shown.PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY;
import static ee.tuleva.onboarding.savings.NavPublication.Shown.PUBLISHED_TODAY;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.TradeDay.SISEKORD_16_P_1_1_3_ORDER_CUTOFF;

import ee.tuleva.onboarding.savings.TradeDay.Day;
import java.time.LocalTime;

public final class NavPublication {

  public static final LocalTime SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT =
      SISEKORD_16_P_1_1_3_ORDER_CUTOFF;

  public enum Shown {
    PUBLISHED_TODAY,
    PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY
  }

  public record Moment(Day day, LocalTime time) {}

  private NavPublication() {}

  public static Shown shown(Moment moment) {
    return moment.day() == WORKING_DAY
            && !moment.time().isBefore(SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT)
        ? PUBLISHED_TODAY
        : PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY;
  }
}
