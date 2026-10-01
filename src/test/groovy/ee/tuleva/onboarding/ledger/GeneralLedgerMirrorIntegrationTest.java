package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EQUITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EXPENSE;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.OFF_BALANCE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.ledger.GeneralLedgerRows.JournalEntryVersion;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(GeneralLedgerStackConfiguration.class)
@Transactional(propagation = NOT_SUPPORTED)
class GeneralLedgerMirrorIntegrationTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String SOURCE = "TEST_SYSTEM";
  private static final double MAX_DELETION_SHARE = 0.05;
  private static final double EVERY_PART_MAY_GO = 1.0;

  private static final GeneralLedgerAccount BANK = account("100100", ASSET, "Bank");
  private static final GeneralLedgerAccount PAYABLES = account("200100", LIABILITY, "Payables");
  private static final GeneralLedgerAccount SHARE_CAPITAL =
      account("300100", EQUITY, "Share capital");
  private static final GeneralLedgerAccount FEE_INCOME = account("400100", INCOME, "Fee income");
  private static final GeneralLedgerAccount OFFICE_COSTS =
      account("500100", EXPENSE, "Office costs");
  private static final GeneralLedgerAccount OFFSETTING =
      account("900000", OFF_BALANCE, "Offsetting");
  private static final List<GeneralLedgerAccount> CHART =
      List.of(BANK, PAYABLES, SHARE_CAPITAL, FEE_INCOME, OFFICE_COSTS, OFFSETTING);

  private static final JournalEntryPart OPENING =
      part("FIN", 1, "2026-01-05", line(BANK, "1000.00"), line(SHARE_CAPITAL, "-1000.00"));
  private static final JournalEntryPart INVOICE =
      part("ARVE", 1, "2026-01-31", line(BANK, "250.00"), line(FEE_INCOME, "-250.00"));

  @Autowired GeneralLedger generalLedger;
  @Autowired GeneralLedgerRows rows;

  @BeforeEach
  void setUp() {
    generalLedger.upsertAccounts(ENTITY, CHART);
  }

  @AfterEach
  void tearDown() {
    rows.deleteAll();
  }

  @Test
  void mirroringABookMakesEveryMonthEndBalanceEqualTheSourceSums() {
    var book =
        List.of(
            OPENING,
            INVOICE,
            part("OST", 1, "2026-02-01", line(OFFICE_COSTS, "80.00"), line(PAYABLES, "-80.00")),
            part("FIN", 2, "2026-02-27", line(PAYABLES, "80.00"), line(BANK, "-80.00")),
            part("PEAKIRI", 1, "2026-02-28", line(OFFICE_COSTS, "12.50"), line(BANK, "-12.50")),
            part("PEAKIRI", 1, "2026-03-02", line(OFFICE_COSTS, "-2.50"), line(BANK, "2.50")),
            part("PEAKIRI", 2, "2026-03-31", line(BANK, "40.00"), line(OFFSETTING, "-40.00")));

    var result = mirror(book);

    assertThat(result).isEqualTo(new MirrorResult(7, 0, 0, 0, 0, 0));
    for (var monthEnd : List.of("2026-01-31", "2026-02-28", "2026-03-31")) {
      assertThat(ledgerBalancesAt(monthEnd)).isEqualTo(sourceSumsAt(book, monthEnd));
    }
    assertThat(rows.balanceAtEndOf(ENTITY, BANK.code(), LocalDate.parse("2026-02-28")))
        .isEqualTo(new BigDecimal("1157.50"));
  }

  @Test
  void remirroringAnUnchangedBookPostsNothing() {
    var book = List.of(OPENING, INVOICE);
    mirror(book);

    var result = mirror(book);

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 0, 2, 0, 0));
    assertThat(rows.count(JOURNAL_ENTRY)).isEqualTo(2);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
  }

  @Test
  void anEditedPartIsReversedAtItsOwnDateAndRepostedAsTheNextRevision() {
    mirror(List.of(OPENING, INVOICE));
    var editedInvoice =
        part("ARVE", 1, "2026-01-31", line(BANK, "275.00"), line(FEE_INCOME, "-275.00"));

    var result = mirror(List.of(OPENING, editedInvoice));

    assertThat(result).isEqualTo(new MirrorResult(0, 1, 0, 1, 0, 0));
    var versions = versionsOf(INVOICE);
    assertThat(versions).extracting(JournalEntryVersion::revision).containsExactly(1, 2);
    assertThat(versions).extracting(JournalEntryVersion::live).containsExactly(false, true);
    assertThat(rows.reversalDateOf(versions.getFirst().id()))
        .contains(versions.getFirst().transactionDate());
    assertThat(versions.getFirst().transactionDate()).isEqualTo(INVOICE.transactionDate());
    assertThat(balanceAt(FEE_INCOME, "2026-01-31")).isEqualTo(new BigDecimal("-275.00"));
  }

  @Test
  void aPartWhoseDocumentTypeChangesIsRepostedAsTheNextRevision() {
    mirror(List.of(OPENING, INVOICE));
    var retypedInvoice =
        new JournalEntryPart(
            INVOICE.sourceKey(), "MUUGIARVE", INVOICE.date(), INVOICE.lines(), Set.of());

    var result = mirror(List.of(OPENING, retypedInvoice));

    assertThat(result).isEqualTo(new MirrorResult(0, 1, 0, 1, 0, 0));
    assertThat(versionsOf(INVOICE))
        .extracting(
            JournalEntryVersion::revision,
            JournalEntryVersion::documentType,
            JournalEntryVersion::live)
        .containsExactly(tuple(1, "ARVE", false), tuple(2, "MUUGIARVE", true));
  }

  @Test
  void aPartGoneFromTheSourceIsReversed() {
    mirror(List.of(OPENING, INVOICE));

    var result = mirror(List.of(OPENING));

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 1, 1, 0, 0));
    var versions = versionsOf(INVOICE);
    assertThat(versions).extracting(JournalEntryVersion::live).containsExactly(false);
    assertThat(rows.reversalDateOf(versions.getFirst().id()))
        .contains(versions.getFirst().transactionDate());
    assertThat(balanceAt(FEE_INCOME, "2026-01-31")).isEqualTo(new BigDecimal("0.00"));
    assertThat(balanceAt(BANK, "2026-01-31")).isEqualTo(new BigDecimal("1000.00"));
  }

  @Test
  void aPartDeletedAndRestoredIsPostedAsTheNextRevision() {
    mirror(List.of(OPENING, INVOICE));
    mirror(List.of(OPENING));

    var result = mirror(List.of(OPENING, INVOICE));

    assertThat(result).isEqualTo(new MirrorResult(1, 0, 0, 1, 0, 0));
    var versions = versionsOf(INVOICE);
    assertThat(versions).extracting(JournalEntryVersion::revision).containsExactly(1, 2);
    assertThat(versions).extracting(JournalEntryVersion::live).containsExactly(false, true);
    assertThat(balanceAt(FEE_INCOME, "2026-01-31")).isEqualTo(new BigDecimal("-250.00"));
  }

  @Test
  void aPartEditedBackToAnEarlierVersionIsPostedAgain() {
    var editedInvoice =
        part("ARVE", 1, "2026-01-31", line(BANK, "275.00"), line(FEE_INCOME, "-275.00"));
    mirror(List.of(OPENING, INVOICE));
    mirror(List.of(OPENING, editedInvoice));

    var result = mirror(List.of(OPENING, INVOICE));

    assertThat(result).isEqualTo(new MirrorResult(0, 1, 0, 1, 0, 0));
    var versions = versionsOf(INVOICE);
    assertThat(versions).extracting(JournalEntryVersion::revision).containsExactly(1, 2, 3);
    assertThat(versions).extracting(JournalEntryVersion::live).containsExactly(false, false, true);
    assertThat(balanceAt(FEE_INCOME, "2026-01-31")).isEqualTo(new BigDecimal("-250.00"));
  }

  @Test
  void aRowMovedToAnotherDateMovesBetweenParts() {
    var januaryRows =
        part("PEAKIRI", 1, "2026-01-30", line(OFFICE_COSTS, "100.00"), line(BANK, "-100.00"));
    var februaryRows =
        part("PEAKIRI", 1, "2026-02-02", line(OFFICE_COSTS, "30.00"), line(PAYABLES, "-30.00"));
    mirror(List.of(OPENING, januaryRows, februaryRows));
    var februaryWithTheMovedRows =
        part(
            "PEAKIRI",
            1,
            "2026-02-02",
            line(OFFICE_COSTS, "100.00"),
            line(BANK, "-100.00"),
            line(OFFICE_COSTS, "30.00"),
            line(PAYABLES, "-30.00"));

    var result = mirror(List.of(OPENING, februaryWithTheMovedRows));

    assertThat(result).isEqualTo(new MirrorResult(0, 1, 1, 1, 0, 0));
    assertThat(balanceAt(OFFICE_COSTS, "2026-01-31")).isEqualTo(new BigDecimal("0.00"));
    assertThat(balanceAt(BANK, "2026-01-31")).isEqualTo(new BigDecimal("1000.00"));
    assertThat(balanceAt(OFFICE_COSTS, "2026-02-28")).isEqualTo(new BigDecimal("130.00"));
    assertThat(balanceAt(BANK, "2026-02-28")).isEqualTo(new BigDecimal("900.00"));
  }

  @Test
  void partsOfAnotherSourceForTheSameEntityAreNeitherAbsentNorReversed() {
    mirror(List.of(OPENING, INVOICE));
    var otherSourcesPart =
        part("FIN", 3, "2026-01-10", line(BANK, "5.00"), line(SHARE_CAPITAL, "-5.00"));

    var otherSourceResult =
        generalLedger.mirror(ENTITY, "OTHER_SYSTEM", List.of(otherSourcesPart), MAX_DELETION_SHARE);
    var remirrorResult = mirror(List.of(OPENING, INVOICE));

    assertThat(otherSourceResult).isEqualTo(new MirrorResult(1, 0, 0, 0, 0, 0));
    assertThat(remirrorResult).isEqualTo(new MirrorResult(0, 0, 0, 2, 0, 0));
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
    assertThat(balanceAt(BANK, "2026-01-31")).isEqualTo(new BigDecimal("1255.00"));
  }

  @Test
  void liveSourceKeysNameTheUnreversedPartsOfOneSource() {
    mirror(List.of(OPENING, INVOICE));
    mirror(List.of(OPENING));
    generalLedger.mirror(
        ENTITY,
        "OTHER_SYSTEM",
        List.of(part("FIN", 3, "2026-01-10", line(BANK, "5.00"), line(SHARE_CAPITAL, "-5.00"))),
        MAX_DELETION_SHARE);

    assertThat(generalLedger.liveSourceKeys(ENTITY, SOURCE)).isEqualTo(Set.of(OPENING.sourceKey()));
  }

  @Test
  void anEmptyResponseWhileLivePartsExistStopsTheEntityWithNothingWritten() {
    mirror(List.of(OPENING, INVOICE));

    assertThatThrownBy(() -> mirror(List.of())).isInstanceOf(IllegalStateException.class);

    assertThat(rows.count(JOURNAL_ENTRY)).isEqualTo(2);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
  }

  @Test
  void aBookMissingMostOfItsPartsReversesNothingUntilTheShareIsRaised() {
    var book =
        IntStream.rangeClosed(1, 12)
            .mapToObj(
                day ->
                    part(
                        "FIN",
                        day,
                        LocalDate.of(2026, 1, day).toString(),
                        line(BANK, "10.00"),
                        line(SHARE_CAPITAL, "-10.00")))
            .toList();
    mirror(book);
    var mostlyMissing = List.of(book.getFirst());

    assertThatThrownBy(() -> mirror(mostlyMissing)).isInstanceOf(IllegalStateException.class);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();

    var result = generalLedger.mirror(ENTITY, SOURCE, mostlyMissing, EVERY_PART_MAY_GO);

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 11, 1, 0, 0));
    assertThat(balanceAt(BANK, "2026-01-31")).isEqualTo(new BigDecimal("10.00"));
  }

  @Test
  void aPartQuarantinedAfterItWasPostedKeepsItsLiveVersionAndIsNotReversed() {
    mirror(List.of(OPENING, INVOICE));
    var invoiceOnAnUnknownAccount =
        part(
            "ARVE",
            1,
            "2026-01-31",
            line(BANK, "250.00"),
            line(accountMissingFromTheChart(), "-250.00"));

    var result = mirror(List.of(OPENING, invoiceOnAnUnknownAccount));

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 0, 1, 1, 1));
    assertThat(versionsOf(INVOICE)).extracting(JournalEntryVersion::live).containsExactly(true);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
    assertThat(balanceAt(FEE_INCOME, "2026-01-31")).isEqualTo(new BigDecimal("-250.00"));
  }

  @Test
  void aQuarantinedPartIsPostedOnceItsAccountAppears() {
    var training = account("500200", EXPENSE, "Training");
    var trainingInvoice =
        part("OST", 2, "2026-01-20", line(training, "60.00"), line(BANK, "-60.00"));

    var quarantined = mirror(List.of(trainingInvoice));
    generalLedger.upsertAccounts(ENTITY, List.of(training));
    var posted = mirror(List.of(trainingInvoice));

    assertThat(quarantined).isEqualTo(new MirrorResult(0, 0, 0, 0, 1, 0));
    assertThat(posted).isEqualTo(new MirrorResult(1, 0, 0, 0, 0, 0));
    assertThat(balanceAt(training, "2026-01-31")).isEqualTo(new BigDecimal("60.00"));
  }

  @Test
  void aPartReplacedByAQuarantinedPartStaysLiveUntilTheReplacementPosts() {
    var training = account("500200", EXPENSE, "Training");
    var purchase =
        part("OST", 1, "2026-02-01", line(OFFICE_COSTS, "80.00"), line(PAYABLES, "-80.00"));
    mirror(List.of(OPENING, purchase));
    var summary =
        new JournalEntryPart(
            "SUMMARY:2026-02",
            "SUMMARY",
            LocalDate.parse("2026-02-28"),
            List.of(
                line(OFFICE_COSTS, "80.00"),
                line(PAYABLES, "-80.00"),
                line(training, "60.00"),
                line(BANK, "-60.00")),
            Set.of(purchase.sourceKey(), "OST:2:2026-02-10"));

    var whileTheReplacementIsQuarantined = mirror(List.of(OPENING, summary));

    assertThat(whileTheReplacementIsQuarantined).isEqualTo(new MirrorResult(0, 0, 0, 1, 1, 0));
    assertThat(versionsOf(purchase)).extracting(JournalEntryVersion::live).containsExactly(true);
    assertThat(balanceAt(OFFICE_COSTS, "2026-02-28")).isEqualTo(new BigDecimal("80.00"));

    generalLedger.upsertAccounts(ENTITY, List.of(training));
    var onceTheReplacementPosts = mirror(List.of(OPENING, summary));

    assertThat(onceTheReplacementPosts).isEqualTo(new MirrorResult(1, 0, 1, 1, 0, 0));
    assertThat(versionsOf(purchase)).extracting(JournalEntryVersion::live).containsExactly(false);
    assertThat(balanceAt(OFFICE_COSTS, "2026-02-28")).isEqualTo(new BigDecimal("80.00"));
    assertThat(balanceAt(training, "2026-02-28")).isEqualTo(new BigDecimal("60.00"));
  }

  @Test
  void aRepostThatFailsLeavesTheReversalUnposted() {
    mirror(List.of(OPENING, INVOICE));
    var invoiceTheLedgerCannotHold =
        part("ARVE", 1, "2026-01-31", line(BANK, "250.005"), line(FEE_INCOME, "-250.005"));

    var result = mirror(List.of(OPENING, invoiceTheLedgerCannotHold));

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 0, 1, 1, 1));
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
    assertThat(versionsOf(INVOICE)).extracting(JournalEntryVersion::live).containsExactly(true);
    assertThat(balanceAt(FEE_INCOME, "2026-01-31")).isEqualTo(new BigDecimal("-250.00"));
  }

  private MirrorResult mirror(List<JournalEntryPart> parts) {
    return generalLedger.mirror(ENTITY, SOURCE, parts, MAX_DELETION_SHARE);
  }

  private List<JournalEntryVersion> versionsOf(JournalEntryPart part) {
    return rows.versionsOf(ENTITY, SOURCE, part.sourceKey());
  }

  private BigDecimal balanceAt(GeneralLedgerAccount account, String date) {
    return rows.balanceAtEndOf(ENTITY, account.code(), LocalDate.parse(date));
  }

  private Map<String, BigDecimal> ledgerBalancesAt(String date) {
    return CHART.stream()
        .collect(toMap(GeneralLedgerAccount::code, account -> balanceAt(account, date)));
  }

  private static Map<String, BigDecimal> sourceSumsAt(List<JournalEntryPart> book, String date) {
    var cutoff = LocalDate.parse(date);
    return CHART.stream()
        .collect(
            toMap(
                GeneralLedgerAccount::code,
                account ->
                    book.stream()
                        .filter(part -> !part.date().isAfter(cutoff))
                        .flatMap(part -> part.lines().stream())
                        .filter(line -> line.accountCode().equals(account.code()))
                        .map(JournalEntryLine::amount)
                        .reduce(ZERO, BigDecimal::add)
                        .setScale(2)));
  }

  private static GeneralLedgerAccount account(String code, AccountType type, String name) {
    return new GeneralLedgerAccount(code, type, Map.of("name", name, "source", SOURCE));
  }

  private static GeneralLedgerAccount accountMissingFromTheChart() {
    return account("999999", EXPENSE, "Not in the chart");
  }

  private static JournalEntryPart part(
      String documentType, int number, String date, JournalEntryLine... lines) {
    return new JournalEntryPart(
        documentType + ":" + number + ":" + date,
        documentType,
        LocalDate.parse(date),
        List.of(lines),
        Set.of());
  }

  private static JournalEntryLine line(GeneralLedgerAccount account, String amount) {
    return new JournalEntryLine(account.code(), new BigDecimal(amount));
  }
}
