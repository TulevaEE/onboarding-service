package ee.tuleva.onboarding.ledger;

import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public record FeeMonthSettlement(List<Revision> revisions) {

  static final int MAX_REVISIONS = 50;

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

  static List<UUID> references(TulevaFund fund, LocalDate feeMonth, SystemAccount feeAccount) {
    return IntStream.range(0, MAX_REVISIONS)
        .mapToObj(revision -> reference(fund, feeMonth, feeAccount, revision))
        .toList();
  }

  static UUID reference(
      TulevaFund fund, LocalDate feeMonth, SystemAccount feeAccount, int revision) {
    LocalDate lastDayOfFeeMonth = feeMonth.plusMonths(1).minusDays(1);
    String key = fund.name() + ":" + lastDayOfFeeMonth + ":" + feeAccount.name() + ":SETTLEMENT";
    String revisedKey = revision == 0 ? key : key + ":" + revision;
    return UUID.nameUUIDFromBytes(revisedKey.getBytes(UTF_8));
  }
}
