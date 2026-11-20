package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SkippedListingCheck.DROPPED_TRADES;
import static ee.tuleva.onboarding.investment.transaction.ingest.SkippedListingCheck.NEVER_LISTED_ORDERS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SkippedListingCheck.REPORT_LOOKUP_FAILED;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SebTradeListingCheckTest {

  private static final PendingListing LISTING =
      new PendingListing(LocalDate.of(2026, 2, 12), Set.of(), Set.of(), Set.of());

  private final LatestPendingReport latestPendingReport = mock(LatestPendingReport.class);
  private final DroppedTradeFinder droppedTradeFinder = mock(DroppedTradeFinder.class);
  private final NeverListedOrderFinder neverListedOrderFinder = mock(NeverListedOrderFinder.class);
  private final SebTradeListingAlerts alerts = mock(SebTradeListingAlerts.class);
  private final SebTradeListingCheck check =
      new SebTradeListingCheck(
          latestPendingReport, droppedTradeFinder, neverListedOrderFinder, alerts);

  @Test
  void aReportLookupThatFails_isReportedAsNotChecked() {
    given(latestPendingReport.listing()).willThrow(new IllegalStateException("database down"));

    check.run();

    then(alerts).should().publish(List.of(), List.of(), List.of(REPORT_LOOKUP_FAILED));
  }

  @Test
  void aDroppedTradeSearchThatFails_isReportedAsNotChecked_andNeverListedOrdersStillGoOut() {
    TransactionOrder neverListed = TransactionOrder.builder().id(1L).build();
    given(latestPendingReport.listing()).willReturn(Optional.of(LISTING));
    given(droppedTradeFinder.find(LISTING)).willThrow(new IllegalStateException("ledger down"));
    given(neverListedOrderFinder.find(LISTING)).willReturn(List.of(neverListed));

    check.run();

    then(alerts).should().publish(List.of(), List.of(neverListed), List.of(DROPPED_TRADES));
  }

  @Test
  void aNeverListedSearchThatFails_isReportedAsNotChecked() {
    given(latestPendingReport.listing()).willReturn(Optional.of(LISTING));
    given(droppedTradeFinder.find(LISTING)).willReturn(new DroppedTrades(List.of(), Set.of()));
    given(neverListedOrderFinder.find(LISTING))
        .willThrow(new IllegalStateException("database down"));

    check.run();

    then(alerts).should().publish(List.of(), List.of(), List.of(NEVER_LISTED_ORDERS));
  }
}
