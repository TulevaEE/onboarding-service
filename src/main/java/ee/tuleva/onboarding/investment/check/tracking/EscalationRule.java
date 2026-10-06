package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
record EscalationRule(int notificationWorkingDay, BigDecimal netTdThreshold, boolean fallback) {

  private static final int SISEKORD_4_P_11_8_NOTIFICATION_WORKING_DAY = 4;
  private static final BigDecimal ESCALATION_NET_TD_THRESHOLD_FALLBACK = new BigDecimal("0.001");

  static EscalationRule on(LocalDate checkDate, TrackingDifferenceCalculator calculator) {
    try {
      return new EscalationRule(
          calculator.escalationThresholdDays(checkDate),
          calculator.escalationNetTdThreshold(checkDate),
          false);
    } catch (Exception e) {
      log.error("Escalation parameters unavailable, using fallback: {}", e.getMessage());
      return new EscalationRule(
          SISEKORD_4_P_11_8_NOTIFICATION_WORKING_DAY, ESCALATION_NET_TD_THRESHOLD_FALLBACK, true);
    }
  }

  Verdict judge(TrackingDifferenceResult result) {
    return new Verdict(result, escalates(result), fallback);
  }

  private boolean escalates(TrackingDifferenceResult result) {
    return result.hasAnyBreach() ? escalatesTheBreach(result) : notifiesTheStreakItEnded(result);
  }

  private boolean escalatesTheBreach(TrackingDifferenceResult result) {
    return result.consecutiveBreachDays() >= notificationWorkingDay
        && ((result.consecutiveNetTd() != null
                && result.consecutiveNetTd().abs().compareTo(netTdThreshold) >= 0)
            || result.escalationNavResidualBreach());
  }

  private boolean notifiesTheStreakItEnded(TrackingDifferenceResult result) {
    var endedStreak = result.endedStreak();
    return endedStreak != null
        && endedStreak.owesTheNotification(notificationWorkingDay, netTdThreshold);
  }

  record Verdict(TrackingDifferenceResult result, boolean escalation, boolean fallback) {

    boolean breached() {
      return result.hasAnyBreach();
    }

    boolean alerts() {
      return breached() || escalation;
    }

    boolean decidedOnFallback() {
      return escalation && fallback;
    }
  }
}
