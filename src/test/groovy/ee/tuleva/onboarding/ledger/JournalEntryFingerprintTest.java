package ee.tuleva.onboarding.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  void keepsTheFingerprintOfLinesWithoutDimensionsThatItHadBeforeLinesCarriedThem() {
    assertThat(JournalEntryFingerprint.of(PART))
        .isEqualTo("1830e713776c3e3bf0412ed06a0061e3f6248fefccf83ef015b20b9289bdc2e0");
  }

  @Test
  void changesWithADimensionsNameOrValue() {
    var tagged = taggedInvoice(Map.of("fund", "FUND1"));
    var otherValue = taggedInvoice(Map.of("fund", "FUND2"));
    var otherName = taggedInvoice(Map.of("team", "FUND1"));

    assertThat(
            List.of(PART, tagged, otherValue, otherName).stream()
                .map(JournalEntryFingerprint::of)
                .distinct())
        .hasSize(4);
  }

  @Test
  void ignoresTheOrderOfDimensions() {
    var teamFirst = new LinkedHashMap<String, String>();
    teamFirst.put("team", "TEAM1");
    teamFirst.put("fund", "FUND1");
    var fundFirst = new LinkedHashMap<String, String>();
    fundFirst.put("fund", "FUND1");
    fundFirst.put("team", "TEAM1");

    assertThat(JournalEntryFingerprint.of(taggedInvoice(teamFirst)))
        .isEqualTo(JournalEntryFingerprint.of(taggedInvoice(fundFirst)));
  }

  @Test
  void changesWithDocumentType() {
    var otherDocumentType =
        new JournalEntryPart(PART.sourceKey(), "FIN", PART.date(), PART.lines(), Set.of());

    assertThat(JournalEntryFingerprint.of(otherDocumentType))
        .isNotEqualTo(JournalEntryFingerprint.of(PART));
  }

  private static JournalEntryPart part(String date, JournalEntryLine... lines) {
    return new JournalEntryPart(
        "ARVE:1:" + date, "ARVE", LocalDate.parse(date), List.of(lines), Set.of());
  }

  private static JournalEntryPart taggedInvoice(Map<String, String> dimensions) {
    return part(
        "2026-01-31",
        line("100100", "250.00"),
        new JournalEntryLine("400100", new BigDecimal("-250.00"), dimensions));
  }

  private static JournalEntryLine line(String accountCode, String amount) {
    return new JournalEntryLine(accountCode, new BigDecimal(amount));
  }
}
