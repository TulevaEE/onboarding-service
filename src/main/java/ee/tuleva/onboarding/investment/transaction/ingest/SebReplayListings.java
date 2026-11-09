package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class SebReplayListings {

  private final Map<String, LocalDate> firstListedOn = new HashMap<>();

  boolean listedBefore(@Nullable String brokerRef) {
    return firstListedOn(brokerRef).isPresent();
  }

  Optional<LocalDate> firstListedOn(@Nullable String brokerRef) {
    return brokerRef == null ? Optional.empty() : Optional.ofNullable(firstListedOn.get(brokerRef));
  }

  void addListedBy(List<SebPendingTransactionRow> rows, LocalDate asOfDate) {
    rows.stream()
        .map(SebPendingTransactionRow::ourRef)
        .filter(Objects::nonNull)
        .forEach(brokerRef -> firstListedOn.putIfAbsent(brokerRef, asOfDate));
  }

  int size() {
    return firstListedOn.size();
  }
}
