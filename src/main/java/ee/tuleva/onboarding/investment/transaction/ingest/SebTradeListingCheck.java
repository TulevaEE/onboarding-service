package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebTradeListingCheck {

  private final LatestPendingReport latestPendingReport;
  private final DroppedTradeFinder droppedTradeFinder;
  private final NeverListedOrderFinder neverListedOrderFinder;
  private final SebTradeListingAlerts alerts;

  void run() {
    Optional<PendingListing> listing;
    try {
      listing = latestPendingReport.listing();
    } catch (RuntimeException e) {
      log.error(
          "SEB trade listing check could not read the pending report: error={}", e.getMessage(), e);
      alerts.publish(List.of(), List.of(), List.of(SkippedListingCheck.REPORT_LOOKUP_FAILED));
      return;
    }
    if (listing.isEmpty()) {
      alerts.publish(List.of(), List.of(), List.of(SkippedListingCheck.UNREADABLE_REPORT));
      return;
    }
    List<SkippedListingCheck> skipped = new ArrayList<>();
    DroppedTrades dropped = droppedTrades(listing.get(), skipped);
    List<TransactionOrder> neverListed = neverListedOrders(listing.get(), skipped);
    dropped.fundsNotChecked().stream()
        .map(SkippedListingCheck::unreadableStatementOf)
        .forEach(skipped::add);
    alerts.publish(dropped.trades(), neverListed, skipped);
    log.info(
        "SEB trade listing check completed: asOfDate={}, droppedCount={}, neverListedCount={},"
            + " skippedCount={}",
        listing.get().asOfDate(),
        dropped.trades().size(),
        neverListed.size(),
        skipped.size());
  }

  private DroppedTrades droppedTrades(PendingListing listing, List<SkippedListingCheck> skipped) {
    try {
      return droppedTradeFinder.find(listing);
    } catch (RuntimeException e) {
      log.error("SEB trade listing check failed on dropped trades: error={}", e.getMessage(), e);
      skipped.add(SkippedListingCheck.DROPPED_TRADES);
      return new DroppedTrades(List.of(), Set.of());
    }
  }

  private List<TransactionOrder> neverListedOrders(
      PendingListing listing, List<SkippedListingCheck> skipped) {
    try {
      return neverListedOrderFinder.find(listing);
    } catch (RuntimeException e) {
      log.error(
          "SEB trade listing check failed on never-listed orders: error={}", e.getMessage(), e);
      skipped.add(SkippedListingCheck.NEVER_LISTED_ORDERS);
      return List.of();
    }
  }
}
