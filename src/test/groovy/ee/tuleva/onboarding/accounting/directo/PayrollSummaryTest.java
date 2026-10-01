package ee.tuleva.onboarding.accounting.directo;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.JournalEntryLine;
import ee.tuleva.onboarding.ledger.JournalEntryPart;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PayrollSummaryTest {

  @Test
  void protectedPartsOfAMonthBecomeOneSummaryPerAccount() {
    var protectedParts =
        List.of(
            part(
                "PALK:4:2026-01-31",
                "PALK",
                "2026-01-31",
                line("540100", "3000.00"),
                line("212301", "-3000.00")),
            part(
                "OST:7:2026-02-03",
                "OST",
                "2026-02-03",
                line("500100", "80.00"),
                line("200100", "-80.00")),
            part(
                "FIN:9:2026-01-15",
                "FIN",
                "2026-01-15",
                line("212301", "2500.00"),
                line("100100", "-2500.00")),
            part(
                "PALK:4:2026-01-30",
                "PALK",
                "2026-01-30",
                line("540100", "2000.00"),
                line("212301", "-2000.00")));

    var summaries = PayrollSummary.of(protectedParts);

    assertThat(summaries)
        .containsExactly(
            summary(
                "2026-01",
                "2026-01-31",
                Set.of("PALK:4:2026-01-31", "FIN:9:2026-01-15", "PALK:4:2026-01-30"),
                line("100100", "-2500.00"),
                line("212301", "-2500.00"),
                line("540100", "5000.00")),
            summary(
                "2026-02",
                "2026-02-28",
                Set.of("OST:7:2026-02-03"),
                line("200100", "-80.00"),
                line("500100", "80.00")));
  }

  @Test
  void anAccountThatNetsToZeroInAMonthLeavesNoLine() {
    var protectedParts =
        List.of(
            part(
                "PALK:4:2026-01-31",
                "PALK",
                "2026-01-31",
                line("540100", "3000.00"),
                line("212301", "-3000.00")),
            part(
                "FIN:9:2026-01-31",
                "FIN",
                "2026-01-31",
                line("212301", "3000.00"),
                line("100100", "-3000.00")));

    assertThat(PayrollSummary.of(protectedParts))
        .containsExactly(
            summary(
                "2026-01",
                "2026-01-31",
                Set.of("PALK:4:2026-01-31", "FIN:9:2026-01-31"),
                line("100100", "-3000.00"),
                line("540100", "3000.00")));
  }

  @Test
  void aMonthWhoseProtectedLinesAllNetToZeroHasNoSummary() {
    var protectedParts =
        List.of(
            part(
                "FIN:9:2026-03-02",
                "FIN",
                "2026-03-02",
                line("212301", "100.00"),
                line("100100", "-100.00")),
            part(
                "FIN:9:2026-03-20",
                "FIN",
                "2026-03-20",
                line("212301", "-100.00"),
                line("100100", "100.00")));

    assertThat(PayrollSummary.of(protectedParts)).isEmpty();
  }

  private static JournalEntryPart part(
      String sourceKey, String documentType, String date, JournalEntryLine... lines) {
    return new JournalEntryPart(
        sourceKey, documentType, LocalDate.parse(date), List.of(lines), Set.of());
  }

  private static JournalEntryPart summary(
      String month, String date, Set<String> replacedSourceKeys, JournalEntryLine... lines) {
    return new JournalEntryPart(
        "PAYROLL:" + month,
        "PAYROLL_SUMMARY",
        LocalDate.parse(date),
        List.of(lines),
        replacedSourceKeys);
  }

  private static JournalEntryLine line(String accountCode, String amount) {
    return new JournalEntryLine(accountCode, new BigDecimal(amount));
  }
}
