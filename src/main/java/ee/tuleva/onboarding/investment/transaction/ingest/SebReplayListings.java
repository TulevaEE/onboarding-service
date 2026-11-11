package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class SebReplayListings {

  private final Map<String, LocalDate> firstListedOn = new HashMap<>();
  private final Map<String, SebPendingTransactionRow> lastListing = new HashMap<>();

  boolean listedBefore(@Nullable String brokerRef) {
    return firstListedOn(brokerRef).isPresent();
  }

  Optional<LocalDate> firstListedOn(@Nullable String brokerRef) {
    return brokerRef == null ? Optional.empty() : Optional.ofNullable(firstListedOn.get(brokerRef));
  }

  Optional<SebPendingTransactionRow> lastListing(@Nullable String brokerRef) {
    return brokerRef == null ? Optional.empty() : Optional.ofNullable(lastListing.get(brokerRef));
  }

  void addListedBy(List<SebPendingTransactionRow> rows, LocalDate asOfDate) {
    for (SebPendingTransactionRow row : rows) {
      String brokerRef = row.ourRef();
      if (brokerRef != null) {
        firstListedOn.putIfAbsent(brokerRef, asOfDate);
        lastListing.put(brokerRef, row);
      }
    }
  }

  int size() {
    return firstListedOn.size();
  }
}
