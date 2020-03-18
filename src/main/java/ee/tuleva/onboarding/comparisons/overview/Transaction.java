package ee.tuleva.onboarding.comparisons.overview;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class Transaction {
  BigDecimal amount;
  LocalDate date;

  public Transaction(BigDecimal amount, Instant date) {
    this.amount = amount;
    this.date = date.atZone(ZoneOffset.UTC).toLocalDate();
  }
}
