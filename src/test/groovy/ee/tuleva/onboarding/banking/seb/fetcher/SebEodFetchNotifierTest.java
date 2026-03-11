package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.mockito.Mockito.verify;

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
    var event = new SebEodFetchFailedEvent(DEPOSIT_EUR, "404 LBR_EOD_STATEMENT_NOT_GENERATED");

    notifier.onFetchFailed(event);

    verify(notificationService)
        .sendMessage(
            "SEB EOD fetch failed: account=DEPOSIT_EUR, error=404 LBR_EOD_STATEMENT_NOT_GENERATED",
            SAVINGS);
  }
}
