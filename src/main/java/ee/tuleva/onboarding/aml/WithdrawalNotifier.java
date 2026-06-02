package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pillar.Pillar;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WithdrawalNotifier {

  private final OperationsNotificationService notificationService;

  public void notifyWithdrawalBatchCreated(
      int age, Set<Pillar> pillars, Set<MandateType> withdrawalTypes, Long mandateBatchId) {
    notificationService.sendMessage(
        formatMessage(age, pillars, withdrawalTypes, mandateBatchId), WITHDRAWALS);
  }

  String formatMessage(
      int age, Set<Pillar> pillars, Set<MandateType> withdrawalTypes, Long mandateBatchId) {
    return "Withdrawal mandate batch created: age=%s, pillars=%s, withdrawalTypes=%s, mandateBatchId=%s"
        .formatted(age, pillars, withdrawalTypes, mandateBatchId);
  }
}
