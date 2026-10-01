package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EQUITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.OFF_BALANCE;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(GeneralLedgerStackConfiguration.class)
@Transactional(propagation = NOT_SUPPORTED)
class GeneralLedgerTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String SOURCE = "TEST_SYSTEM";
  private static final double SHARE = 0.05;
  private static final List<GeneralLedgerAccount> CHART =
      List.of(account("100100", ASSET, "Bank"), account("400100", INCOME, "Fee income"));

  @Autowired GeneralLedger generalLedger;
  @Autowired GeneralLedgerAccounts generalLedgerAccounts;
  @Autowired JdbcClient jdbcClient;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired LedgerTransactionService transactionService;
  @Autowired GeneralLedgerRows rows;

  @AfterEach
  void tearDown() {
    rows.deleteAll();
  }

  @Test
  void createsMissingAccountsWithTypeAndMetadata() {
    generalLedger.upsertAccounts(
        ENTITY,
        List.of(account("100100", ASSET, "Bank"), account("300100", EQUITY, "Share capital")));

    assertThat(generalLedgerAccounts.resolve(ENTITY, "100100"))
        .extracting(
            LedgerAccount::getName,
            LedgerAccount::getPurpose,
            LedgerAccount::getAccountType,
            LedgerAccount::getAssetType,
            LedgerAccount::getOwner,
            LedgerAccount::getMetadata)
        .containsExactly(
            "GENERAL_LEDGER:TULEVA_FONDID:100100",
            SYSTEM_ACCOUNT,
            ASSET,
            EUR,
            null,
            Map.of("name", "Bank"));
    assertThat(generalLedgerAccounts.resolve(ENTITY, "300100"))
        .extracting(LedgerAccount::getAccountType, LedgerAccount::getMetadata)
        .containsExactly(EQUITY, Map.of("name", "Share capital"));
  }

  @Test
  void renamedAccountUpdatesItsMetadataOnly() {
    generalLedger.upsertAccounts(ENTITY, List.of(account("100100", ASSET, "Bank")));
    var original = generalLedgerAccounts.resolve(ENTITY, "100100");

    generalLedger.upsertAccounts(ENTITY, List.of(account("100100", ASSET, "Main bank")));

    assertThat(generalLedgerAccounts.resolve(ENTITY, "100100"))
        .extracting(LedgerAccount::getId, LedgerAccount::getAccountType, LedgerAccount::getMetadata)
        .containsExactly(original.getId(), ASSET, Map.of("name", "Main bank"));
    assertThat(accountsNamed("GENERAL_LEDGER:TULEVA_FONDID:100100")).isEqualTo(1);
  }

  @Test
  void aReclassifiedAccountStopsTheEntityInsteadOfCreatingASibling() {
    generalLedger.upsertAccounts(ENTITY, List.of(account("200100", ASSET, "Prepayments")));

    assertThatThrownBy(
            () ->
                generalLedger.upsertAccounts(
                    ENTITY, List.of(account("200100", LIABILITY, "Prepayments"))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(accountsNamed("GENERAL_LEDGER:TULEVA_FONDID:200100")).isEqualTo(1);
    assertThat(generalLedgerAccounts.resolve(ENTITY, "200100").getAccountType()).isEqualTo(ASSET);
  }

  @Test
  void looksUpByNameAlone() {
    generalLedger.upsertAccounts(ENTITY, List.of(account("900000", OFF_BALANCE, "Offsetting")));

    assertThat(generalLedgerAccounts.resolve(ENTITY, "900000"))
        .extracting(LedgerAccount::getName, LedgerAccount::getAccountType)
        .containsExactly("GENERAL_LEDGER:TULEVA_FONDID:900000", OFF_BALANCE);
  }

  @Test
  void twoLiveVersionsOfOnePartStopTheEntity() {
    generalLedger.upsertAccounts(ENTITY, CHART);
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));
    var reference = JournalEntryWriter.reference(ENTITY, SOURCE, invoice.sourceKey());
    postJournalEntry(reference);
    postJournalEntry(reference);

    assertThatThrownBy(() -> generalLedger.mirror(ENTITY, SOURCE, List.of(invoice), SHARE))
        .isInstanceOf(IllegalStateException.class);

    assertThat(rows.count(JOURNAL_ENTRY)).isEqualTo(2);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
  }

  @Test
  void anEmptySourceWithNothingLiveMirrorsNothing() {
    generalLedger.upsertAccounts(ENTITY, CHART);

    var result = generalLedger.mirror(ENTITY, SOURCE, List.of(), SHARE);

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 0, 0, 0, 0));
  }

  @Test
  void aBookMissingExactlyTheAbsoluteLimitOfPartsIsReversedNotStopped() {
    generalLedger.upsertAccounts(ENTITY, CHART);
    var invoices =
        IntStream.rangeClosed(1, 11)
            .mapToObj(n -> part("ARVE", n, line("100100", "250.00"), line("400100", "-250.00")))
            .toList();
    generalLedger.mirror(ENTITY, SOURCE, invoices, SHARE);

    var result = generalLedger.mirror(ENTITY, SOURCE, invoices.subList(0, 1), SHARE);

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 10, 1, 0, 0));
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isEqualTo(10);
  }

  @Test
  void anUnbalancedOrSingleLinePartIsQuarantined() {
    generalLedger.upsertAccounts(ENTITY, CHART);
    var unbalanced = part("ARVE", 1, line("100100", "250.00"), line("400100", "-240.00"));
    var singleLine = part("FIN", 1, line("100100", "0.00"));

    var result = generalLedger.mirror(ENTITY, SOURCE, List.of(unbalanced, singleLine), SHARE);

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 0, 0, 2, 0));
    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
  }

  @Test
  void aSourceKeyGivenTwiceStopsTheEntityWithNothingWritten() {
    generalLedger.upsertAccounts(ENTITY, CHART);
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));
    var sameKeyOtherAmount = part("ARVE", 1, line("100100", "275.00"), line("400100", "-275.00"));

    assertThatThrownBy(
            () -> generalLedger.mirror(ENTITY, SOURCE, List.of(invoice, sameKeyOtherAmount), SHARE))
        .isInstanceOf(IllegalStateException.class);

    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
  }

  @Test
  void aMirrorCalledInsideACallersTransactionStopsWithNothingWritten() {
    generalLedger.upsertAccounts(ENTITY, CHART);
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> generalLedger.mirror(ENTITY, SOURCE, List.of(invoice), SHARE)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
  }

  @Test
  void anAccountOfALookAlikeEntityDoesNotCountAsKnown() {
    generalLedger.upsertAccounts(ENTITY, List.of(account("100100", ASSET, "Bank")));
    generalLedger.upsertAccounts("TULEVAXFONDID", List.of(account("400100", INCOME, "Income")));
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));

    var result = generalLedger.mirror(ENTITY, SOURCE, List.of(invoice), SHARE);

    assertThat(result).isEqualTo(new MirrorResult(0, 0, 0, 0, 1, 0));
  }

  @Test
  void anEntityNameWithAColonIsRejected() {
    var entityWithAColon = "TULEVA:FONDID";
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));

    assertThatThrownBy(() -> generalLedger.upsertAccounts(entityWithAColon, CHART))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> generalLedger.mirror(entityWithAColon, SOURCE, List.of(invoice), SHARE))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(accountsNamed("GENERAL_LEDGER:TULEVA:FONDID:100100")).isZero();
    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
  }

  @Test
  void aSourceNameWithAColonIsRejected() {
    generalLedger.upsertAccounts(ENTITY, CHART);
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));

    assertThatThrownBy(() -> generalLedger.mirror(ENTITY, "DIRECTO:ARVE", List.of(invoice), SHARE))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
  }

  @Test
  void aBlankEntityNameIsRejected() {
    var blankEntity = " ";
    var invoice = part("ARVE", 1, line("100100", "250.00"), line("400100", "-250.00"));

    assertThatThrownBy(() -> generalLedger.upsertAccounts(blankEntity, CHART))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> generalLedger.mirror(blankEntity, SOURCE, List.of(invoice), SHARE))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(accountsNamed("GENERAL_LEDGER: :100100")).isZero();
    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
  }

  private void postJournalEntry(UUID reference) {
    var amount = new BigDecimal("250.00");
    transactionTemplate.executeWithoutResult(
        status ->
            transactionService.createTransaction(
                JOURNAL_ENTRY,
                Instant.parse("2026-01-31T21:59:59.999999Z"),
                reference,
                Map.of("fingerprint", "stale", "revision", 1, "source", SOURCE),
                new LedgerEntryDto(generalLedgerAccounts.resolve(ENTITY, "100100"), amount),
                new LedgerEntryDto(
                    generalLedgerAccounts.resolve(ENTITY, "400100"), amount.negate())));
  }

  private static JournalEntryPart part(String documentType, int number, JournalEntryLine... lines) {
    return new JournalEntryPart(
        documentType + ":" + number + ":2026-01-31",
        documentType,
        LocalDate.parse("2026-01-31"),
        List.of(lines));
  }

  private static JournalEntryLine line(String accountCode, String amount) {
    return new JournalEntryLine(accountCode, new BigDecimal(amount));
  }

  private long accountsNamed(String name) {
    return jdbcClient
        .sql("SELECT count(*) FROM ledger.account WHERE name = :name")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  private static GeneralLedgerAccount account(String code, AccountType type, String name) {
    return new GeneralLedgerAccount(code, type, Map.of("name", name));
  }
}
