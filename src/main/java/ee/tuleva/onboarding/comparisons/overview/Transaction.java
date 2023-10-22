package ee.tuleva.onboarding.comparisons.overview;

import static java.util.Comparator.comparing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.jetbrains.annotations.NotNull;

public record Transaction(BigDecimal amount, Instant time) implements Comparable<Transaction> {

  public LocalDate date() {
    return time.atZone(ZoneOffset.UTC).toLocalDate();
  }

  @Override
  public int compareTo(@NotNull Transaction other) {
    return comparing(Transaction::time).thenComparing(Transaction::amount).compare(this, other);
  }
}
