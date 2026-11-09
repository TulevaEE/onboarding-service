package ee.tuleva.onboarding.investment.transaction.ingest;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SebReplayListings {

  private final Set<String> brokerRefs = new HashSet<>();

  boolean listedBefore(@Nullable String brokerRef) {
    return brokerRef != null && brokerRefs.contains(brokerRef);
  }

  void addListedBy(List<SebPendingTransactionRow> rows) {
    rows.stream()
        .map(SebPendingTransactionRow::ourRef)
        .filter(Objects::nonNull)
        .forEach(brokerRefs::add);
  }

  int size() {
    return brokerRefs.size();
  }
}
