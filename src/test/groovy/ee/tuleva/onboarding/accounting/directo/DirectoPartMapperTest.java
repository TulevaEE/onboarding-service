package ee.tuleva.onboarding.accounting.directo;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EXPENSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.GeneralLedgerAccount;
import ee.tuleva.onboarding.ledger.JournalEntryLine;
import ee.tuleva.onboarding.ledger.JournalEntryPart;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DirectoPartMapperTest {

  private static final List<DirectoAccount> CHART =
      List.of(
          new DirectoAccount("100100", "Bank account", "0", null, null),
          new DirectoAccount("300100", "Share capital", "2", null, null),
          new DirectoAccount("400100", "Fee income", "3", null, null),
          new DirectoAccount("500100", "Office rent", "4", null, null));

  private final DirectoPartMapper mapper = new DirectoPartMapper();

  @Test
  void amountIsDebitMinusCreditInWholeCents() {
    var book =
        mapper.map(
            book(
                transaction(
                    "FIN",
                    "1",
                    "2026-01-05T00:00:00",
                    row("100100", "1000.0000", null),
                    row("300100", null, "999.5000"),
                    row("400100", "10.0000", "10.5000"))));

    assertThat(book.parts())
        .containsExactly(
            part(
                "FIN:1:2026-01-05",
                "FIN",
                "2026-01-05",
                line("100100", "1000.00"),
                line("300100", "-999.50"),
                line("400100", "-0.50")));
  }

  @Test
  void aSubCentAmountFailsTheDocument() {
    var book =
        book(
            transaction(
                "FIN",
                "1",
                "2026-01-05T00:00:00",
                row("100100", "10.0050", null),
                row("300100", null, "10.0050")));

    assertThatThrownBy(() -> mapper.map(book)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void zeroLinesAreDroppedStornoLinesKept() {
    var book =
        mapper.map(
            book(
                transaction(
                    "PEAKIRI",
                    "3",
                    "2026-03-02T00:00:00",
                    row("500100", "-2.5000", null),
                    row("100100", null, "-2.5000"),
                    row("400100", "0.0000", "0.0000"),
                    row("400100", null, null)),
                transaction(
                    "PEAKIRI",
                    "4",
                    "2026-03-03T00:00:00",
                    row("500100", "0.0000", null),
                    row("100100", null, "0.0000"))));

    assertThat(book.parts())
        .containsExactly(
            part(
                "PEAKIRI:3:2026-03-02",
                "PEAKIRI",
                "2026-03-02",
                line("500100", "-2.50"),
                line("100100", "2.50")));
  }

  @Test
  void rowsSplitIntoPartsByEffectiveDate() {
    var book =
        mapper.map(
            book(
                transaction(
                    "PEAKIRI",
                    "3",
                    "2026-02-27T00:00:00",
                    dated(row("500100", "12.5000", null), "2026-03-02T00:00:00"),
                    dated(row("100100", null, "12.5000"), "2026-03-02T00:00:00"),
                    dated(row("500100", "7.0000", null), "2026-02-27T00:00:00"),
                    row("100100", null, "7.0000"))));

    assertThat(book.parts())
        .containsExactly(
            part(
                "PEAKIRI:3:2026-02-27",
                "PEAKIRI",
                "2026-02-27",
                line("500100", "7.00"),
                line("100100", "-7.00")),
            part(
                "PEAKIRI:3:2026-03-02",
                "PEAKIRI",
                "2026-03-02",
                line("500100", "12.50"),
                line("100100", "-12.50")));
  }

  @Test
  void theSameNumberUnderAnotherTypeIsAnotherDocument() {
    var book =
        mapper.map(
            book(
                transaction(
                    "FIN",
                    "1",
                    "2026-01-05T00:00:00",
                    row("100100", "5.0000", null),
                    row("300100", null, "5.0000")),
                transaction(
                    "ARVE",
                    "1",
                    "2026-01-05T00:00:00",
                    row("100100", "6.0000", null),
                    row("400100", null, "6.0000"))));

    assertThat(book.parts())
        .extracting(JournalEntryPart::sourceKey)
        .containsExactly("FIN:1:2026-01-05", "ARVE:1:2026-01-05");
  }

  @Test
  void aTypeAndNumberGivenTwiceFailsTheEntity() {
    var book =
        book(
            transaction(
                "FIN",
                "1",
                "2026-01-05T00:00:00",
                row("100100", "5.0000", null),
                row("300100", null, "5.0000")),
            transaction(
                "FIN",
                "1",
                "2026-01-06T00:00:00",
                row("100100", "6.0000", null),
                row("300100", null, "6.0000")));

    assertThatThrownBy(() -> mapper.map(book)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aMalformedDateFailsFast() {
    var book =
        book(
            transaction(
                "FIN",
                "1",
                "05.01.2026",
                row("100100", "5.0000", null),
                row("300100", null, "5.0000")));

    assertThatThrownBy(() -> mapper.map(book)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anUnknownAccountClassFailsTheEntity() {
    var book =
        new DirectoBook(
            List.of(new DirectoAccount("700100", "Statistics", "7", null, null)),
            List.of(),
            List.of());

    assertThatThrownBy(() -> mapper.map(book)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void accountMetadataCarriesTheChartAsPlainStrings() {
    var book =
        mapper.map(
            new DirectoBook(
                List.of(
                    new DirectoAccount(
                        "100100",
                        "Arvelduskonto",
                        "0",
                        "1010",
                        List.of(
                            new DirectoDatafield("MUU", "ENG", "Another field in English"),
                            new DirectoDatafield("LISANIMI", "FIN", "Kayttotili"),
                            new DirectoDatafield("LISANIMI", "ENG", "Current account"),
                            new DirectoDatafield("RV_OTSE", null, "R1"),
                            new DirectoDatafield("RV_KAUDNE", "", "R2"),
                            new DirectoDatafield("RV_KAUDNE_KORR", null, "R3"),
                            new DirectoDatafield("MUU", null, "ignored"))),
                    new DirectoAccount("500100", "Office rent", "4", "", List.of())),
                List.of(),
                List.of()));

    assertThat(book.accounts())
        .containsExactly(
            new GeneralLedgerAccount(
                "100100",
                ASSET,
                Map.of(
                    "name", "Arvelduskonto",
                    "nameEnglish", "Current account",
                    "class", "0",
                    "correspondenceCode", "1010",
                    "cashFlowDirect", "R1",
                    "cashFlowIndirect", "R2",
                    "cashFlowIndirectAdjustment", "R3",
                    "source", "DIRECTO")),
            new GeneralLedgerAccount(
                "500100",
                EXPENSE,
                Map.of("name", "Office rent", "class", "4", "source", "DIRECTO")));
  }

  @Test
  void ordinaryDocumentsAreNotProtected() {
    var book =
        mapper.map(
            book(
                transaction(
                    "FIN",
                    "1",
                    "2026-01-05T00:00:00",
                    row("100100", "5.0000", null),
                    row("300100", null, "5.0000"))));

    assertThat(book.protectedSourceKeys()).isEqualTo(Set.of());
  }

  private static DirectoBook book(DirectoTransaction... transactions) {
    return new DirectoBook(CHART, List.of(), List.of(transactions));
  }

  private static DirectoTransaction transaction(
      String type, String number, String date, DirectoRow... rows) {
    return new DirectoTransaction(type, number, date, List.of(rows));
  }

  private static DirectoRow row(String account, @Nullable String debit, @Nullable String credit) {
    return new DirectoRow(account, amount(debit), amount(credit), null, null, null, null, null);
  }

  private static DirectoRow dated(DirectoRow row, String date) {
    return new DirectoRow(
        row.account(),
        row.debit(),
        row.credit(),
        row.object(),
        row.project(),
        row.supplier(),
        row.customer(),
        date);
  }

  private static @Nullable BigDecimal amount(@Nullable String amount) {
    return amount == null ? null : new BigDecimal(amount);
  }

  private static JournalEntryPart part(
      String sourceKey, String documentType, String date, JournalEntryLine... lines) {
    return new JournalEntryPart(
        sourceKey, documentType, LocalDate.parse(date), List.of(lines), Set.of());
  }

  private static JournalEntryLine line(String accountCode, String amount) {
    return new JournalEntryLine(accountCode, new BigDecimal(amount));
  }
}
