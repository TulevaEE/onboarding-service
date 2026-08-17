package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebEodFetchNotifierTest {

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private SebEodFetchNotifier notifier;

  @Test
  void onFetchFailed_sendsNotification() {
    var event =
        new SebEodFetchFailedEvent(
            new BankAccount("EE111111111111111111", DEPOSIT_EUR, TKF100, "gw-test"),
            "404 LBR_EOD_STATEMENT_NOT_GENERATED");

    notifier.onFetchFailed(event);

    verify(notificationService)
        .sendMessage(
            "SEB EOD fetch failed: account=TKF100:DEPOSIT_EUR, error=404 LBR_EOD_STATEMENT_NOT_GENERATED",
            SAVINGS);
  }
}
