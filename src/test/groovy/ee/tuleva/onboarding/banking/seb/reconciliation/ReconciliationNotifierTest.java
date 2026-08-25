package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationNotifierTest {

  private static final BankAccount DEPOSIT_ACCOUNT =
      new BankAccount("EE111111111111111111", DEPOSIT_EUR, TKF100, "gw-test");

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private ReconciliationNotifier notifier;

  @Test
  void onReconciliationCompleted_sendsOkNotification_whenMatched() {
    var event =
        new ReconciliationCompletedEvent(
            DEPOSIT_ACCOUNT, new BigDecimal("1000.00"), new BigDecimal("1000.00"), true);

    notifier.onReconciliationCompleted(event);

    verify(notificationService)
        .sendMessage(
            "✅ Bank reconciliation OK: bankAccount=TKF100:DEPOSIT_EUR, balance=1000.00", SAVINGS);
  }

  @Test
  void onReconciliationCompleted_sendsFailedNotification_whenNotMatched() {
    var event =
        new ReconciliationCompletedEvent(
            DEPOSIT_ACCOUNT, new BigDecimal("1000.00"), new BigDecimal("999.99"), false);

    notifier.onReconciliationCompleted(event);

    verify(notificationService)
        .sendMessage(
            "🔴 Bank reconciliation FAILED: bankAccount=TKF100:DEPOSIT_EUR, bankBalance=1000.00, ledgerBalance=999.99, diff=0.01 <!channel>",
            SAVINGS);
  }
}
