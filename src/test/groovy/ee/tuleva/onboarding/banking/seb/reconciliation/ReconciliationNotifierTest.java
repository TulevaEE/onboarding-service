package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationNotifierTest {

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private ReconciliationNotifier notifier;

  @Test
  void onReconciliationCompleted_sendsOkNotification_whenMatched() {
    var event =
        new ReconciliationCompletedEvent(
            DEPOSIT_EUR, new BigDecimal("1000.00"), new BigDecimal("1000.00"), true);

    notifier.onReconciliationCompleted(event);

    verify(notificationService)
        .sendMessage("Bank reconciliation OK: bankAccount=DEPOSIT_EUR, balance=1000.00", SAVINGS);
  }

  @Test
  void onReconciliationCompleted_sendsFailedNotification_whenNotMatched() {
    var event =
        new ReconciliationCompletedEvent(
            DEPOSIT_EUR, new BigDecimal("1000.00"), new BigDecimal("999.99"), false);

    notifier.onReconciliationCompleted(event);

    verify(notificationService)
        .sendMessage(
            "Bank reconciliation FAILED: bankAccount=DEPOSIT_EUR, bankBalance=1000.00, ledgerBalance=999.99, diff=0.01",
            SAVINGS);
  }
}
