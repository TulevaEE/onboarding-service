package ee.tuleva.onboarding.banking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentApprovalBriefFormatterTest {

  private final PaymentApprovalBriefFormatter formatter = new PaymentApprovalBriefFormatter();

  @Test
  void showsACountAndATotalPerAccountBecauseThatIsWhatIsComparedAgainstTheBank() {
    var text = formatter.format(brief(false, 0));

    assertThat(text).contains("WITHDRAWAL_EUR", "7 payments", "12,345.67");
    assertThat(text).contains("TOTAL", "8 payments");
  }

  @Test
  void carriesNoNamesNoIbansAndNoPerClientLines() {
    var text = formatter.format(brief(false, 0));

    assertThat(text).doesNotContain("EE");
    assertThat(text).doesNotContain("…");
    // seven payouts, one line
    assertThat(text.lines().filter(line -> line.contains("payouts to clients")).count())
        .isEqualTo(1);
  }

  @Test
  void anAccountThatWouldGoNegativeIsCalledOut() {
    var text = formatter.format(brief(true, 0));

    assertThat(text).contains("after execution", "would go negative");
  }

  @Test
  void namesAHoldRatherThanQuietlyShowingOneFewerPayment() {
    var text = formatter.format(brief(true, 1));

    assertThat(text).contains("ATTENTION", "HELD", "PAYMENT_BLOCKED");
  }

  @Test
  void anEmptyDayStillProducesABriefSoSilenceIsNeverAmbiguous() {
    var text =
        formatter.format(
            new PaymentApprovalBrief(
                LocalDate.of(2026, 8, 10), List.of(), List.of(), 0, List.of(), false));

    assertThat(text).contains("no payments pending approval");
  }

  // Shown as an equation rather than a tick: it is the one number a signatory can verify in their
  // head, and a tick would hide the two figures being compared.
  @Test
  void theCrossAccountTieIsPrintedWithBothItsNumbers() {
    var text = formatter.format(brief(false, 0));

    assertThat(text)
        .contains("Checks:", "payouts == transfer to withdrawal account", "12,345.67 = 12,345.67");
  }

  @Test
  void aVerdictThatDidNotPassIsNotTicked() {
    var text = formatter.format(brief(true, 0));

    assertThat(text).contains("🟠 payout entitlement", "1 held");
    assertThat(text).doesNotContain("✅ payout entitlement");
  }

  private static PaymentApprovalBrief brief(boolean attention, int held) {
    return new PaymentApprovalBrief(
        LocalDate.of(2026, 8, 10),
        List.of(
            new PaymentApprovalBrief.AccountSummary(
                "FUND_INVESTMENT_EUR",
                List.of(
                    new PaymentApprovalBrief.FlowSummary(
                        "to withdrawal account", 1, new BigDecimal("12345.67"))),
                1,
                new BigDecimal("12345.67"),
                null),
            new PaymentApprovalBrief.AccountSummary(
                "WITHDRAWAL_EUR",
                List.of(
                    new PaymentApprovalBrief.FlowSummary(
                        "payouts to clients", 7, new BigDecimal("12345.67"))),
                7,
                new BigDecimal("12345.67"),
                new BigDecimal("-1.00"))),
        List.of(
            new PaymentApprovalBrief.Verdict(
                "payouts == transfer to withdrawal account", true, "12,345.67 = 12,345.67"),
            new PaymentApprovalBrief.Verdict(
                "file integrity (XSD + parse-back)", !attention, attention ? "1 held" : null),
            new PaymentApprovalBrief.Verdict(
                "payout entitlement", !attention, attention ? "1 held" : null)),
        held,
        held > 0 ? List.of("PAYMENT_BLOCKED: file does not match the request") : List.of(),
        attention);
  }
}
