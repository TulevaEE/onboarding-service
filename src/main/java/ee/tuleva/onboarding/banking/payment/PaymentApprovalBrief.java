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
    List<Verdict> verdicts,
    int heldCount,
    List<String> heldReasons,
    boolean attention) {

  /**
   * What stood between these payments and the bank, stated as something true of today rather than
   * as decoration. A gate is green because it held nothing; one that held something says so. The
   * cross-account tie carries both of its numbers, because that is the one figure a signatory can
   * verify without leaving the message.
   *
   * @param detail null when there is nothing to add to a green verdict.
   */
  public record Verdict(
      String label, boolean passed, @org.jspecify.annotations.Nullable String detail) {}

  /**
   * @param projectedBalance what the account is left with once these payments execute, or null when
   *     the bank could not tell us its balance. Informational: it has never been watched against
   *     reality, so nothing is gated on it.
   */
  public record AccountSummary(
      String accountName,
      List<FlowSummary> flows,
      int paymentCount,
      BigDecimal total,
      @org.jspecify.annotations.Nullable BigDecimal projectedBalance) {

    public boolean goesNegative() {
      return projectedBalance != null && projectedBalance.signum() < 0;
    }
  }

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

  /** One home for the money format, so the tie reads the same way as the totals above it. */
  public static String amount(BigDecimal value) {
    return String.format(java.util.Locale.ROOT, "%,.2f", value);
  }
}
