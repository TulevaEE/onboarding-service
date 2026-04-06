package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingDifferenceNotifierTest {

  @Mock OperationsNotificationService notificationService;

  @InjectMocks TrackingDifferenceNotifier notifier;

  @Test
  void sendsAllClearWhenNoBreaches() {
    var result = result(false, 0, BigDecimal.ZERO);

    notifier.notify(List.of(result));

    then(notificationService)
        .should()
        .sendMessage(contains("all funds within limits"), eq(INVESTMENT));
  }

  @Test
  void sendsBreachNotification() {
    var result = result(true, 1, new BigDecimal("0.0015"));

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("TD BREACH DETECTED"), eq(INVESTMENT));
  }

  @Test
  void includesTopContributors() {
    var attributions =
        List.of(
            new SecurityAttribution(
                "IE00A",
                new BigDecimal("0.60"),
                new BigDecimal("0.55"),
                new BigDecimal("-0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("-0.001")),
            new SecurityAttribution(
                "IE00B",
                new BigDecimal("0.40"),
                new BigDecimal("0.45"),
                new BigDecimal("0.05"),
                new BigDecimal("-0.01"),
                new BigDecimal("-0.0005")));

    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.0015"))
            .fundReturn(new BigDecimal("0.0100"))
            .benchmarkReturn(new BigDecimal("0.0085"))
            .breach(true)
            .consecutiveBreachDays(1)
            .consecutiveNetTd(new BigDecimal("0.0015"))
            .securityAttributions(attributions)
            .cashDrag(new BigDecimal("-0.001"))
            .feeDrag(new BigDecimal("-0.00001"))
            .residual(new BigDecimal("0.00051"))
            .build();

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("IE00A"), eq(INVESTMENT));
  }

  @Test
  void sendsEscalationWhenThreeConsecutiveDaysAndNetSumExceedsThreshold() {
    var result = result(true, 3, new BigDecimal("0.0045"));

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("TD ESCALATION"), eq(INVESTMENT));
  }

  @Test
  void noEscalationWhenNetSumBelowThreshold() {
    // 3 consecutive days but alternating +/- that net to below threshold
    var result = result(true, 3, new BigDecimal("0.0005"));

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("TD BREACH DETECTED"), eq(INVESTMENT));
  }

  private TrackingDifferenceResult result(
      boolean breach, int consecutiveBreachDays, BigDecimal consecutiveNetTd) {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(LocalDate.of(2026, 4, 3))
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(breach ? new BigDecimal("0.0015") : new BigDecimal("0.0005"))
        .fundReturn(new BigDecimal("0.0100"))
        .benchmarkReturn(new BigDecimal("0.0085"))
        .breach(breach)
        .consecutiveBreachDays(consecutiveBreachDays)
        .consecutiveNetTd(consecutiveNetTd)
        .securityAttributions(List.of())
        .cashDrag(BigDecimal.ZERO)
        .feeDrag(BigDecimal.ZERO)
        .residual(BigDecimal.ZERO)
        .build();
  }
}
