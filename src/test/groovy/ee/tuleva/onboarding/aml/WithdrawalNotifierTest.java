package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WithdrawalNotifierTest {

  private OperationsNotificationService notificationService;
  private WithdrawalNotifier notifier;

  @BeforeEach
  void setUp() {
    notificationService = Mockito.mock(OperationsNotificationService.class);
    notifier = new WithdrawalNotifier(notificationService);
  }

  @Test
  @DisplayName("sends a withdrawal batch created Slack message to the withdrawals channel")
  void notifyWithdrawalBatchCreated_sendsSlackMessage() {
    notifier.notifyWithdrawalBatchCreated(65, Set.of(SECOND), Set.of(FUND_PENSION_OPENING), 42L);

    verify(notificationService)
        .sendMessage(
            "Withdrawal mandate batch created: age=65, pillars=[SECOND], withdrawalTypes=[FUND_PENSION_OPENING], mandateBatchId=42",
            WITHDRAWALS);
  }

  @Test
  @DisplayName("does not silently swallow a Slack send failure")
  void notifyWithdrawalBatchCreated_whenSlackFails_propagatesException() {
    doThrow(new IllegalStateException("Slack down"))
        .when(notificationService)
        .sendMessage(any(), any());

    assertThatThrownBy(
            () ->
                notifier.notifyWithdrawalBatchCreated(
                    65, Set.of(SECOND), Set.of(FUND_PENSION_OPENING), 42L))
        .isInstanceOf(IllegalStateException.class);
  }
}
