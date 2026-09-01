package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pillar.Pillar;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WithdrawalNotifier {

  private final OperationsNotificationService notificationService;

  @EventListener
  public void onWithdrawalBatchCreated(WithdrawalBatchCreated event) {
    notificationService.sendMessage(
        formatMessage(
            event.age(), event.pillars(), event.withdrawalTypes(), event.mandateBatchId()),
        WITHDRAWALS);
  }

  String formatMessage(
      int age, Set<Pillar> pillars, Set<MandateType> withdrawalTypes, Long mandateBatchId) {
    return "Withdrawal mandate batch created: age=%s, pillars=%s, withdrawalTypes=%s, mandateBatchId=%s"
        .formatted(age, pillars, withdrawalTypes, mandateBatchId);
  }
}
