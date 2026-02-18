package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TransactionBatchNotifier {

  private static final Map<String, String> DRIVE_URL_LABELS =
      Map.of(
          "sebFundXlsx", "SEB indeksfondid",
          "sebEtfXlsx", "SEB ETF",
          "ftEtfXlsx", "FT ETF");

  private final OperationsNotificationService notificationService;

  @EventListener
  void onBatchFinalized(BatchFinalizedEvent event) {
    try {
      String header =
          "Transaction batch finalized: batchId=%s, %d orders, tradeDate=%s"
              .formatted(event.batchId(), event.orderCount(), event.tradeDate());

      String driveLinks =
          event.driveFileUrls() == null || event.driveFileUrls().isEmpty()
              ? ""
              : "\n"
                  + DRIVE_URL_LABELS.entrySet().stream()
                      .filter(entry -> event.driveFileUrls().containsKey(entry.getKey()))
                      .map(
                          entry ->
                              "%s: %s"
                                  .formatted(
                                      entry.getValue(), event.driveFileUrls().get(entry.getKey())))
                      .collect(joining("\n", "\n", ""));

      notificationService.sendMessage(header + driveLinks, INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send batch finalization notification: batchId={}", event.batchId(), e);
    }
  }
}
