package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.fund.nav.NavDeadline.Report.MISSING;

import ee.tuleva.onboarding.savings.TradeDay.Day;
import java.time.LocalTime;

public final class NavDeadline {

  public static final LocalTime SISEKORD_4_P_11_3_1_PILLAR_2_NAV_DEADLINE = LocalTime.of(11, 30);
  public static final LocalTime SISEKORD_4_P_11_3_2_PILLAR_3_AND_TKF_NAV_DEADLINE =
      LocalTime.of(15, 30);

  public enum Report {
    PUBLISHED,
    MISSING,
    NOT_CHECKED
  }

  public record Status(Day day, Report report) {}

  private NavDeadline() {}

  public static boolean isLate(Status status) {
    return status.day() == WORKING_DAY && status.report() == MISSING;
  }
}
