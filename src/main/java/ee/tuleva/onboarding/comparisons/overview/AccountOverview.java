package ee.tuleva.onboarding.comparisons.overview;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

  public LocalDate getStartDate() {
    return startTime.atOffset(ZoneOffset.UTC).toLocalDate();
  }

  public LocalDate getEndDate() {
    return endTime.atOffset(ZoneOffset.UTC).toLocalDate();
  }
}
