package ee.tuleva.onboarding.banking.message;

import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record StoredStatement(StatementPeriod period, @Nullable Instant processedAt) {

  public StoredStatement(LocalDate from, LocalDate to, @Nullable Instant processedAt) {
    this(new StatementPeriod(from, to), processedAt);
  }

  public boolean isProcessed() {
    return processedAt != null;
  }
}
