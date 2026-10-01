package ee.tuleva.onboarding.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalEntryFingerprintTest {

  private static final JournalEntryPart PART =
      part("2026-01-31", line("100100", "250.00"), line("400100", "-250.00"));

  @Test
  void ignoresLineOrderAndAmountScale() {
    var reorderedAndRescaled = part("2026-01-31", line("400100", "-250"), line("100100", "250.0"));

    assertThat(JournalEntryFingerprint.of(reorderedAndRescaled))
        .isEqualTo(JournalEntryFingerprint.of(PART));
  }

  @Test
  void changesWithAmountAccountOrDate() {
    var otherAmount = part("2026-01-31", line("100100", "275.00"), line("400100", "-275.00"));
    var otherAccount = part("2026-01-31", line("100200", "250.00"), line("400100", "-250.00"));
    var otherDate = part("2026-02-01", line("100100", "250.00"), line("400100", "-250.00"));

    assertThat(
            List.of(PART, otherAmount, otherAccount, otherDate).stream()
                .map(JournalEntryFingerprint::of)
                .distinct())
        .hasSize(4);
  }

  @Test
  void changesWithDocumentType() {
    var otherDocumentType =
        new JournalEntryPart(PART.sourceKey(), "FIN", PART.date(), PART.lines());

    assertThat(JournalEntryFingerprint.of(otherDocumentType))
        .isNotEqualTo(JournalEntryFingerprint.of(PART));
  }

  private static JournalEntryPart part(String date, JournalEntryLine... lines) {
    return new JournalEntryPart("ARVE:1:" + date, "ARVE", LocalDate.parse(date), List.of(lines));
  }

  private static JournalEntryLine line(String accountCode, String amount) {
    return new JournalEntryLine(accountCode, new BigDecimal(amount));
  }
}
