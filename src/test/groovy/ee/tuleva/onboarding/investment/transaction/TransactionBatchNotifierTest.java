package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionBatchNotifierTest {

  private static final LocalDate TRADE_DATE = LocalDate.parse("2026-01-15");

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private TransactionBatchNotifier notifier;

  @Test
  void onBatchFinalized_namesEveryBatchSentTogetherInOneSlackMessage() {
    var event = new BatchFinalizedEvent(List.of(1L, 2L), 5, TRADE_DATE, Map.of());

    notifier.onBatchFinalized(event);

    verify(notificationService)
        .sendMessage(
            "Tehingukorraldused 15.01.2026 märgitud saadetuks: 5 korraldust, batch'id 1, 2",
            INVESTMENT);
  }

  @Test
  void onBatchFinalized_countsASingleOrderInTheSingular() {
    var event = new BatchFinalizedEvent(List.of(7L), 1, TRADE_DATE, Map.of());

    notifier.onBatchFinalized(event);

    verify(notificationService)
        .sendMessage(
            "Tehingukorraldused 15.01.2026 märgitud saadetuks: 1 korraldus, batch'id 7",
            INVESTMENT);
  }

  @Test
  void onBatchFinalized_listsTheDaysFilesOnDriveInBrokerFileOrder() {
    var urls =
        Map.of(
            "sebFundXlsx", "https://drive.google.com/seb-fund",
            "ftEtfXlsx", "https://drive.google.com/ft-etf",
            "uuidWorkbookXlsx", "https://drive.google.com/uuid-workbook");
    var event = new BatchFinalizedEvent(List.of(1L, 2L), 5, TRADE_DATE, urls);

    notifier.onBatchFinalized(event);

    verify(notificationService)
        .sendMessage(
            """
            Tehingukorraldused 15.01.2026 märgitud saadetuks: 5 korraldust, batch'id 1, 2

            SEB indeksfondid: https://drive.google.com/seb-fund
            FT ETF: https://drive.google.com/ft-etf
            UUID workbook: https://drive.google.com/uuid-workbook""",
            INVESTMENT);
  }

  @Test
  void onBatchFinalized_whenNotificationFails_doesNotPropagate() {
    var event = new BatchFinalizedEvent(List.of(1L, 2L), 3, TRADE_DATE, Map.of());
    doThrow(new RuntimeException("Slack unavailable"))
        .when(notificationService)
        .sendMessage(any(), eq(INVESTMENT));

    assertThatCode(() -> notifier.onBatchFinalized(event)).doesNotThrowAnyException();
  }
}
