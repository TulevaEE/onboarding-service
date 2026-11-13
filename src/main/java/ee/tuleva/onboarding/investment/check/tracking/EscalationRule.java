package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Residual.BREACHED;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Residual.WITHIN;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Today.BREACH;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Today.CLEAN;

import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.investment.check.tracking.ConsecutiveBreachTracker.ConsecutiveBreachInfo;
import ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Escalation;
import ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Streak;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
record EscalationRule(int notificationWorkingDay, BigDecimal netTdThreshold, boolean fallback) {

  private static final TrackingCheckType SISEKORD_4_P_11_8_CHECK = MODEL_PORTFOLIO;
  static final int SISEKORD_4_P_11_8_NOTIFICATION_WORKING_DAY = 4;
  static final BigDecimal ESCALATION_NET_TD_THRESHOLD_FALLBACK = new BigDecimal("0.001");

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
    return new Verdict(result, escalates(result), notificationWorkingDay, fallback);
  }

  private boolean escalates(TrackingDifferenceResult result) {
    if (result.checkType() != SISEKORD_4_P_11_8_CHECK) {
      return false;
    }
    return TdEscalation.escalates(
        new Escalation(
            result.hasAnyBreach() ? BREACH : CLEAN,
            new Streak(
                result.consecutiveBreachDays(),
                result.consecutiveBreachDays(),
                result.consecutiveNetTd(),
                result.escalationNavResidualBreach() ? BREACHED : WITHIN),
            before(result.streakBefore()),
            notificationWorkingDay,
            netTdThreshold));
  }

  private static @Nullable Streak before(@Nullable ConsecutiveBreachInfo streakBefore) {
    if (streakBefore == null) {
      return null;
    }
    return new Streak(
        streakBefore.count(),
        streakBefore.streakDaysBeforeTheCheckDate(),
        streakBefore.compoundedTd(),
        streakBefore.hadNavResidualBreach() ? BREACHED : WITHIN);
  }

  record Verdict(
      TrackingDifferenceResult result,
      boolean escalation,
      int notificationWorkingDay,
      boolean fallback) {

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
