package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Residual.BREACHED;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Today.BREACH;
import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public final class TdEscalation {

  public enum Today {
    BREACH,
    CLEAN
  }

  public enum Residual {
    BREACHED,
    WITHIN
  }

  public record Streak(int checkedDays, int days, @Nullable BigDecimal netTd, Residual residual) {}

  public record Escalation(
      Today today,
      Streak throughToday,
      @Nullable Streak before,
      int notificationWorkingDay,
      BigDecimal netTdThreshold) {}

  private TdEscalation() {}

  public static boolean escalates(Escalation escalation) {
    return todaysStreakEscalates(escalation) || earlierStreakEscalatesLate(escalation);
  }

  private static boolean todaysStreakEscalates(Escalation escalation) {
    var streak = escalation.throughToday();
    var netTd = streak.netTd();
    return escalation.today() == BREACH
        && streak.days() >= escalation.notificationWorkingDay()
        && ((netTd != null && netTd.abs().compareTo(escalation.netTdThreshold()) >= 0)
            || streak.residual() == BREACHED);
  }

  private static boolean earlierStreakEscalatesLate(Escalation escalation) {
    var before = escalation.before();
    return before != null
        && before.checkedDays() < escalation.notificationWorkingDay()
        && before.days() >= escalation.notificationWorkingDay() - 1
        && (requireNonNull(before.netTd()).abs().compareTo(escalation.netTdThreshold()) >= 0
            || before.residual() == BREACHED);
  }
}
