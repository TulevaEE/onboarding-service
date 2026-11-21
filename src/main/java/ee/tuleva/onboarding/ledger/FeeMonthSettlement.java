package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FeeMonthSettlement(List<Revision> revisions) {

  public record Revision(LocalDate settledOn, BigDecimal amount) {}

  public BigDecimal amount() {
    return revisions.stream().map(Revision::amount).reduce(ZERO, BigDecimal::add);
  }

  public BigDecimal settledThrough(LocalDate date) {
    return revisions.stream()
        .filter(revision -> !revision.settledOn().isAfter(date))
        .map(Revision::amount)
        .reduce(ZERO, BigDecimal::add);
  }
}
