package ee.tuleva.onboarding.comparisons.overview;

import static java.util.Comparator.comparing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public record Transaction(BigDecimal amount, Instant time, @Nullable String isin)
    implements Comparable<Transaction> {

  public Transaction(BigDecimal amount, Instant time) {
    this(amount, time, null);
  }

  public LocalDate date() {
    return time.atZone(ZoneOffset.UTC).toLocalDate();
  }

  @Override
  public int compareTo(@NotNull Transaction other) {
    return comparing(Transaction::time).thenComparing(Transaction::amount).compare(this, other);
  }

  @Override
  public String toString() {
    return "{" + amount + ", " + time + "}";
  }
}
