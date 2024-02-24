package ee.tuleva.onboarding.comparisons.overview;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AccountOverview {
  List<Transaction> transactions;
  BigDecimal beginningBalance;
  BigDecimal endingBalance;
  Instant startTime;
  Instant endTime;
  Integer pillar;

  public AccountOverview sort() {
    Collections.sort(transactions);
    return this;
  }

  public LocalDate calculateRealBeginningDate() {
    if (beginningBalance != null && beginningBalance.compareTo(ZERO) != 0) {
      return getStartDate();
    }
    if (transactions != null && !transactions.isEmpty()) {
      return transactions.get(0).date();
    }
    // fallback
    return getStartDate();
  }

  private LocalDate getStartDate() {
    return startTime.atOffset(UTC).toLocalDate();
  }

  public LocalDate getEndDate() {
    return endTime.atOffset(UTC).toLocalDate();
  }
}
