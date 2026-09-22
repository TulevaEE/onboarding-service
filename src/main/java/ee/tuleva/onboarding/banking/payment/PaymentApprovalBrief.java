package ee.tuleva.onboarding.banking.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public record PaymentApprovalBrief(
    LocalDate date,
    List<AccountSummary> accounts,
    List<Verdict> verdicts,
    int heldCount,
    List<String> heldReasons,
    boolean attention) {
  public record Verdict(String label, boolean passed, @Nullable String detail) {}

  public record AccountSummary(
      String accountName,
      List<FlowSummary> flows,
      int paymentCount,
      BigDecimal total,
      @Nullable BigDecimal projectedBalance) {
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

  public static String amount(BigDecimal value) {
    return String.format(Locale.ROOT, "%,.2f", value);
  }

  public static String count(int payments) {
    return payments == 1 ? "1 payment" : payments + " payments";
  }
}
