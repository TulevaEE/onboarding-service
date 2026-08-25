package ee.tuleva.onboarding.banking.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the signatories compare against the bank's pending list.
 *
 * <p>Deliberately aggregates only. No names, no IBANs, not even masked ones: the checks establish
 * who each payment is for, automatically, so there is nothing here for a human to re-read — and
 * per-client lines would put client data in a chat channel for no gain. Any extra, missing or
 * duplicated payment moves a count or a total, which is what the comparison is for.
 */
public record PaymentApprovalBrief(
    LocalDate date,
    List<AccountSummary> accounts,
    int heldCount,
    List<String> heldReasons,
    boolean attention) {

  public record AccountSummary(
      String accountName, List<FlowSummary> flows, int paymentCount, BigDecimal total) {}

  public record FlowSummary(String label, int paymentCount, BigDecimal total) {}

  public boolean isEmpty() {
    return accounts.isEmpty() && heldCount == 0;
  }

  public int totalPaymentCount() {
    return accounts.stream().mapToInt(AccountSummary::paymentCount).sum();
  }

  public BigDecimal grandTotal() {
    return accounts.stream().map(AccountSummary::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
