package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SebStatementGapNotifier {

  private final OperationsNotificationService notificationService;

  @EventListener
  public void onGapsFound(SebStatementGapsFound event) {
    try {
      notificationService.sendMessage(message(event.gaps()), SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send SEB statement gap notification", e);
    }
  }

  private static String message(List<StatementGap> gaps) {
    var earliest = gaps.stream().map(gap -> gap.period().from()).min(naturalOrder()).orElseThrow();
    var latest = gaps.stream().map(gap -> gap.period().to()).max(naturalOrder()).orElseThrow();
    return ("🔴 SEB statements not booked in the ledger: %s. "
            + "Missing statements are re-fetched every 30 minutes until 23:30; "
            + "a statement that failed processing has failed_at set on its banking_message. "
            + "If this persists, run POST /admin/fetch-seb-history?from=%s&to=%s <!channel>")
        .formatted(
            gaps.stream().map(SebStatementGapNotifier::describe).collect(joining(", ")),
            earliest,
            latest);
  }

  private static String describe(StatementGap gap) {
    var period = gap.period();
    return period.from().equals(period.to())
        ? "%s %s".formatted(gap.account(), period.from())
        : "%s %s to %s".formatted(gap.account(), period.from(), period.to());
  }
}
