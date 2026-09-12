package ee.tuleva.onboarding.banking.payment;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Renders the brief for the ops channel.
 *
 * <p>Ordered for the eye: the two numbers compared against the bank's screen first, then anything
 * held. A green brief has to be legible in one glance on a phone, since approvals frequently happen
 * away from a desk.
 */
@Component
public class PaymentApprovalBriefFormatter {

  public String format(PaymentApprovalBrief brief) {
    if (brief.isEmpty()) {
      // Sent anyway. "No brief, no approval" only works if a brief always arrives, otherwise
      // silence cannot be told apart from a job that died.
      return "%s TKF100 — no payments pending approval (%s)"
          .formatted(brief.attention() ? "🟠" : "✅", brief.date());
    }

    var text = new StringBuilder();
    text.append(brief.attention() ? "🟠 ATTENTION — " : "✅ ");
    text.append("TKF100 payments pending approval — ").append(brief.date()).append("\n");

    for (var account : brief.accounts()) {
      text.append("\n  ")
          .append(account.accountName())
          .append("  ")
          .append(count(account.paymentCount()))
          .append("  ")
          .append(amount(account.total()))
          .append(" EUR\n");
      if (account.projectedBalance() != null) {
        text.append("      after execution: ")
            .append(amount(account.projectedBalance()))
            .append(account.goesNegative() ? "  ⚠️ would go negative" : "")
            .append("\n");
      }
      for (var flow : account.flows()) {
        text.append("      ")
            .append(flow.label())
            .append("  ")
            .append(count(flow.paymentCount()))
            .append("  ")
            .append(amount(flow.total()))
            .append("\n");
      }
    }

    text.append("\n  TOTAL  ")
        .append(count(brief.totalPaymentCount()))
        .append("  ")
        .append(amount(brief.grandTotal()))
        .append(" EUR\n");

    if (brief.heldCount() > 0) {
      text.append("\n  HELD — not sent to the bank: ").append(brief.heldCount()).append("\n");
      brief.heldReasons().forEach(reason -> text.append("      ").append(reason).append("\n"));
    }

    text.append(
        "\n  Approve per account. Each account's count and total must match its bank list.");
    return text.toString();
  }

  private static String count(int payments) {
    return payments == 1 ? "1 payment" : payments + " payments";
  }

  private static String amount(BigDecimal value) {
    return String.format(Locale.ROOT, "%,.2f", value);
  }
}
