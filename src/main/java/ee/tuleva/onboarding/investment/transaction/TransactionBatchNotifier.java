package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.transaction.export.ExportFile;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TransactionBatchNotifier {

  private static final DateTimeFormatter TRADE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final OperationsNotificationService notificationService;

  @EventListener
  void onBatchFinalized(BatchFinalizedEvent event) {
    try {
      String header =
          "Tehingukorraldused %s märgitud saadetuks: %s, batch'id %s"
              .formatted(
                  TRADE_DATE.format(event.tradeDate()),
                  orders(event.orderCount()),
                  event.batchIds().stream().map(String::valueOf).collect(joining(", ")));

      String driveLinks =
          event.driveFileUrls() == null || event.driveFileUrls().isEmpty()
              ? ""
              : "\n"
                  + ExportFile.brokerFiles().stream()
                      .filter(file -> event.driveFileUrls().containsKey(file.metadataKey()))
                      .map(
                          file ->
                              "%s: %s"
                                  .formatted(
                                      file.driveLabel(),
                                      event.driveFileUrls().get(file.metadataKey())))
                      .collect(joining("\n", "\n", ""));

      notificationService.sendMessage(header + driveLinks, INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send batch finalization notification: batchIds={}", event.batchIds(), e);
    }
  }

  private static String orders(int count) {
    return count == 1 ? "1 korraldus" : count + " korraldust";
  }
}
