package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.util.Map;

public record JournalEntryLine(
    String accountCode, BigDecimal amount, Map<String, String> dimensions) {

  public JournalEntryLine(String accountCode, BigDecimal amount) {
    this(accountCode, amount, Map.of());
  }

  public JournalEntryLine {
    dimensions = Map.copyOf(dimensions);
  }
}
