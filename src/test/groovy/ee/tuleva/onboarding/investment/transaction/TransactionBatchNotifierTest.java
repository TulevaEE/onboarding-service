package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionBatchNotifierTest {

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private TransactionBatchNotifier notifier;

  @Test
  void onBatchFinalized_sendsSlackNotification() {
    var event = new BatchFinalizedEvent(1L, 5, "2026-01-15", Map.of());

    notifier.onBatchFinalized(event);

    verify(notificationService).sendMessage(any(String.class), eq(INVESTMENT));
  }

  @Test
  void onBatchFinalized_includesDriveUrlsInMessage() {
    var urls =
        Map.of(
            "sebFundXlsx", "https://drive.google.com/seb-fund",
            "ftEtfXlsx", "https://drive.google.com/ft-etf");
    var event = new BatchFinalizedEvent(1L, 5, "2026-01-15", urls);

    notifier.onBatchFinalized(event);

    var messageCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(messageCaptor.capture(), eq(INVESTMENT));

    var message = messageCaptor.getValue();
    assertThat(message).contains("SEB indeksfondid: https://drive.google.com/seb-fund");
    assertThat(message).contains("FT ETF: https://drive.google.com/ft-etf");
  }

  @Test
  void onBatchFinalized_whenNotificationFails_doesNotPropagate() {
    var event = new BatchFinalizedEvent(1L, 3, "2026-01-20", Map.of());
    doThrow(new RuntimeException("Slack unavailable"))
        .when(notificationService)
        .sendMessage(any(), eq(INVESTMENT));

    assertThatCode(() -> notifier.onBatchFinalized(event)).doesNotThrowAnyException();
  }
}
