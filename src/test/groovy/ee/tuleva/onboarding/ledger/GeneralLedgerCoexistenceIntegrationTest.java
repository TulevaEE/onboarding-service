package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.NAV_EQUITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

@DataJpaTest
@Import({
  GeneralLedgerStackConfiguration.class,
  SavingsFundLedgerStackConfiguration.class,
  FundBankLedger.class,
  NavFeeAccrualLedger.class,
  NavLedgerRepository.class
})
class GeneralLedgerCoexistenceIntegrationTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String SOURCE = "TEST_SYSTEM";
  private static final double MAX_DELETION_SHARE = 0.05;
  private static final LocalDate DATE = LocalDate.parse("2026-01-31");

  private static final GeneralLedgerAccount BANK = account("100100", ASSET);
  private static final GeneralLedgerAccount PAYABLES = account("200100", LIABILITY);
  private static final GeneralLedgerAccount FEE_INCOME = account("400100", INCOME);

  @Autowired GeneralLedger generalLedger;
  @Autowired NavFeeAccrualLedger navFeeAccrualLedger;
  @Autowired NavLedgerRepository navLedgerRepository;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired GeneralLedgerRows rows;
  @Autowired JdbcClient jdbcClient;

  @BeforeTransaction
  void mirrorABookWithARevisionAndAPayableInDebit() {
    generalLedger.upsertAccounts(ENTITY, List.of(BANK, PAYABLES, FEE_INCOME));
    var invoice = part("ARVE:1", line(BANK, "250.00"), line(FEE_INCOME, "-250.00"));
    var prepaidInvoice = part("OST:1", line(PAYABLES, "80.00"), line(BANK, "-80.00"));
    mirror(List.of(invoice, prepaidInvoice));
    mirror(
        List.of(part("ARVE:1", line(BANK, "275.00"), line(FEE_INCOME, "-275.00")), prepaidInvoice));
  }

  @AfterTransaction
  void deleteTheMirroredBook() {
    rows.deleteAll();
  }

  @Test
  void navBalancesIgnoreGeneralLedgerAccounts() {
    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, DATE, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("12.34"), Map.of());

    assertThat(
            navLedgerRepository.getSystemAccountBalance(
                MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100)))
        .isEqualByComparingTo("-12.34");
    assertThat(navLedgerRepository.getSystemAccountBalance(NAV_EQUITY.getAccountName(TKF100)))
        .isEqualByComparingTo("12.34");
    assertThat(navLedgerRepository.getSecuritiesUnitBalances(TKF100)).isEmpty();
  }

  @Test
  void bankEntryReplayIgnoresJournalEntryReferences() {
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isEqualTo(1);
    var bankEntryReference = UUID.nameUUIDFromBytes("TEST-ACCOUNT:entry-1".getBytes(UTF_8));

    assertThat(fundBankLedger.existsForExternalReference(bankEntryReference)).isFalse();
    fundBankLedger.recordBankFee(
        TKF100, new BigDecimal("1.50"), bankEntryReference, FUND_INVESTMENT_CASH_CLEARING, DATE);
    assertThat(fundBankLedger.existsForExternalReference(bankEntryReference)).isTrue();
  }

  @Test
  void integrityChecksIgnoreGeneralLedgerAccounts() {
    assertThat(rows.balanceAtEndOf(ENTITY, PAYABLES.code(), DATE))
        .isEqualTo(new BigDecimal("80.00"));

    assertThat(savingsFundLedger.findHolderAccountIdsInDebit()).isEmpty();
    assertThat(savingsFundLedger.findPayoutIdsBookedToAnotherPartyThanPriced()).isEmpty();
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TKF100)).isZero();
  }

  @Test
  void adminAdjustmentsRejectGeneralLedgerAccounts() {
    long entriesBefore = generalLedgerEntries();

    assertThatThrownBy(
            () ->
                savingsFundLedger.recordAdjustment(
                    "GENERAL_LEDGER:TULEVA_FONDID:100100",
                    null,
                    "GENERAL_LEDGER:TULEVA_FONDID:400100",
                    null,
                    new BigDecimal("10.00"),
                    null,
                    "Manual correction"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(generalLedgerEntries()).isEqualTo(entriesBefore);
  }

  private long generalLedgerEntries() {
    return jdbcClient
        .sql(
            """
            SELECT count(*) FROM ledger.entry e JOIN ledger.account a ON a.id = e.account_id
            WHERE a.name LIKE 'GENERAL\\_LEDGER:%' ESCAPE '\\'
            """)
        .query(Long.class)
        .single();
  }

  private void mirror(List<JournalEntryPart> parts) {
    generalLedger.mirror(ENTITY, SOURCE, parts, MAX_DELETION_SHARE);
  }

  private static GeneralLedgerAccount account(String code, AccountType type) {
    return new GeneralLedgerAccount(code, type, Map.of("name", "Account " + code));
  }

  private static JournalEntryPart part(String document, JournalEntryLine... lines) {
    return new JournalEntryPart(
        document + ":" + DATE, document.split(":")[0], DATE, List.of(lines));
  }

  private static JournalEntryLine line(GeneralLedgerAccount account, String amount) {
    return new JournalEntryLine(account.code(), new BigDecimal(amount));
  }
}
