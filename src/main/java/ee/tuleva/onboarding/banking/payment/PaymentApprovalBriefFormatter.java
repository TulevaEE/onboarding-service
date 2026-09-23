package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.amount;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.count;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class PaymentApprovalBriefFormatter {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DATE_AND_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  public String format(PaymentApprovalBrief brief) {
    if (brief.isEmpty()) {
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
      var projected = account.projectedBalance();
      if (projected != null) {
        text.append("      after execution: ")
            .append(amount(projected.amount()))
            .append(" (balance as of ")
            .append(statementTime(projected.asOf(), brief.date()))
            .append(" statement)")
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

    if (!brief.verdicts().isEmpty()) {
      text.append("\n  Checks:\n");
      for (var verdict : brief.verdicts()) {
        text.append("      ").append(verdict.passed() ? "✅ " : "🟠 ").append(verdict.label());
        if (verdict.detail() != null) {
          text.append("  (").append(verdict.detail()).append(")");
        }
        text.append("\n");
      }
    }

    if (brief.heldCount() > 0) {
      text.append("\n  HELD — not sent to the bank: ").append(brief.heldCount()).append("\n");
      brief.heldReasons().forEach(reason -> text.append("      ").append(reason).append("\n"));
    }

    text.append(
        "\n  Approve per account. Each account's count and total must match its bank list.");
    return text.toString();
  }

  private static String statementTime(Instant asOf, LocalDate briefDate) {
    var local = asOf.atZone(TALLINN);
    return (local.toLocalDate().equals(briefDate) ? TIME_OF_DAY : DATE_AND_TIME).format(local);
  }
}
