package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INTEREST_INCOME;
import static ee.tuleva.onboarding.ledger.SystemAccount.REGISTRAR_CASH_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_CUSTODY;
import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_CASH_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNCLASSIFIED_BANK_ENTRY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SebIntegrationTest
class SuspenseReclassificationServiceTest {

  private static final Instant AFTER_EVERY_BOOKING = Instant.parse("2100-01-01T00:00:00Z");

  private static final String REGISTRAR_IBAN = "EE001234567890123477";
  private static final String OWN_ACCOUNT_IBAN = "EE001234567890123490";
  private static final String BANK_FEE_IBAN = "EE001234567890123491";
  private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 2, 10);
  private static final String SUBFUND_ISIN = "IE00BFG1TM61";

  @Autowired private SuspenseReclassificationService service;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private LedgerService ledgerService;
  @Autowired private NavLedgerRepository navLedgerRepository;

  @DynamicPropertySource
  static void counterpartyIbans(DynamicPropertyRegistry registry) {
    registry.add("seb-gateway.registrar-ibans", () -> REGISTRAR_IBAN);
    registry.add("seb-gateway.own-account-ibans", () -> OWN_ACCOUNT_IBAN);
    registry.add("seb-gateway.bank-fee-ibans", () -> BANK_FEE_IBAN);
  }

  @Test
  void reclassify_supportsOwnAccountTransfersBankFeesAndManagerRebates() {
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("1633975.32"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "TULEVA MAAILMA AKTSIATE PENSIONIFOND",
            OWN_ACCOUNT_IBAN,
            "Ülekanne fondi teisele kontole",
            "ESCT"));
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("-140.00"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "Swedbank AS", BANK_FEE_IBAN, "Arve nr 03-03-2026-3", "ESCT"));
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("34720.54"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "Tuleva Fondid AS", "EE001234567890123488", "TUK75 BR rebate", "ESCT"));

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(3, 0));
    assertThat(balance(SystemAccount.OWN_ACCOUNT_TRANSFER))
        .isEqualByComparingTo(new BigDecimal("-1633975.32"));
    assertThat(balance(SystemAccount.BANK_FEE)).isEqualByComparingTo(new BigDecimal("140.00"));
    assertThat(balance(SystemAccount.MANAGEMENT_FEE_REBATE))
        .isEqualByComparingTo(new BigDecimal("-34720.54"));
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntriesBefore(TUK75, AFTER_EVERY_BOOKING))
        .isZero();
  }

  @Test
  void reclassify_leavesAManagerCreditNotStatedAsARebateInSuspense() {
    parkManagerCredit("100.00", "muu ülekanne");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    assertThat(balance(SystemAccount.MANAGEMENT_FEE_REBATE)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(new BigDecimal("-100.00"));
  }

  private void parkManagerCredit(String amount, String remittanceInformation) {
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal(amount),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "Tuleva Fondid AS", "EE001234567890123488", remittanceInformation, "RCDT"));
  }

  @Test
  void reclassify_movesRegistrarEntriesOutOfSuspenseAndLeavesUnknownOnes() {
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("1000000.00"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "AS Pensionikeskus", REGISTRAR_IBAN, "osakute laekumine", null));
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("-250000.00"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "AS Pensionikeskus", REGISTRAR_IBAN, "tagasivõtmine", null));
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("99.99"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "Mystery Counterparty OU", "EE001234567890123499", "selgituseta", "XXXX"));

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(2, 1));
    assertThat(balance(REGISTRAR_CASH_SETTLEMENT))
        .isEqualByComparingTo(new BigDecimal("-750000.00"));
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(new BigDecimal("-99.99"));
    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING))
        .isEqualByComparingTo(new BigDecimal("750099.99"));
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntriesBefore(TUK75, AFTER_EVERY_BOOKING))
        .isEqualTo(1);

    var replay = service.reclassify(TUK75);

    assertThat(replay).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    assertThat(balance(REGISTRAR_CASH_SETTLEMENT))
        .isEqualByComparingTo(new BigDecimal("-750000.00"));
  }

  @Test
  void
      reclassify_movesAParkedTradeSettlementIntoTheInstrumentAccounts_andLeavesAnUnknownTickerParked() {
    parkBankOperation(
        "-34448.28",
        "DLA0000001/BDWTEIA/1000/34.44828/Buy/ BlackRock Asset Management Ireland Ltd",
        "SUBS");
    parkBankOperation("10.00", "DLA0000002/NOTLISTED/5/2.00/Sell/", "TRAD");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(1, 1));
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntriesBefore(TUK75, AFTER_EVERY_BOOKING))
        .isEqualTo(1);
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(new BigDecimal("-10.00"));
    assertThat(instrumentBalance(SECURITIES_CUSTODY)).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(instrumentBalance(TRADE_CASH_SETTLEMENT))
        .isEqualByComparingTo(new BigDecimal("34448.28"));
    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING))
        .isEqualByComparingTo(new BigDecimal("-34438.28"));

    var replay = service.reclassify(TUK75);

    assertThat(replay).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    assertThat(instrumentBalance(SECURITIES_CUSTODY)).isEqualByComparingTo(new BigDecimal("1000"));
  }

  @Test
  void reclassify_movesParkedInterestAndBankAdjustmentsOutOfSuspense() {
    parkBankOperation("2.00", "Intress", "INTR");
    parkBankOperation("-3.00", "Korrigeerimine", "ADJT");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(2, 0));
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntriesBefore(TUK75, AFTER_EVERY_BOOKING))
        .isZero();
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(balance(INTEREST_INCOME)).isEqualByComparingTo(new BigDecimal("-2.00"));
    assertThat(balance(SystemAccount.BANK_ADJUSTMENT)).isEqualByComparingTo(new BigDecimal("3.00"));
    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING))
        .isEqualByComparingTo(new BigDecimal("-1.00"));
  }

  @Test
  void
      reclassify_leavesAnUnknownPayersOtherCreditInSuspense_whenTheStatementGaveItsIbanButNoName() {
    parkFromCounterparty("99.99", null, "EE001234567890123499", "selgituseta", "OTHR");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    assertThat(balance(SystemAccount.BANK_ADJUSTMENT)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(new BigDecimal("-99.99"));
  }

  @Test
  void reclassify_movesARegistrarEntryOutOfSuspense_whenTheStatementGaveItsIbanButNoName() {
    parkFromCounterparty("500.00", null, REGISTRAR_IBAN, "osakute laekumine", "ESCT");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(1, 0));
    assertThat(balance(REGISTRAR_CASH_SETTLEMENT)).isEqualByComparingTo(new BigDecimal("-500.00"));
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void reclassify_takesParkedSalesAndRedemptionsOutOfCustody_andParkedBuysIntoIt() {
    parkBankOperation("-34448.28", "DLA0000001/BDWTEIA/1000/34.44828/Buy/", "TRAD");
    parkBankOperation("50000.00", "DLA0000004/BDWTEIA/1450.25/34.477/Sell/", "TRAD");
    parkBankOperation("3447.70", "DLA0000005/BDWTEIA/100/34.477/Sell/", "REDM");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(3, 0));
    assertThat(instrumentBalance(SECURITIES_CUSTODY))
        .isEqualByComparingTo(new BigDecimal("-550.25"));
    assertThat(instrumentBalance(TRADE_CASH_SETTLEMENT))
        .isEqualByComparingTo(new BigDecimal("-18999.42"));
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void reclassify_leavesAnEntryItCannotReadInSuspenseAndCarriesOnWithTheRest() {
    parkBankOperation("2.00", "Intress", "INTR");
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("7.00"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(null, null, null, "OTHR"));
    parkBankOperation(
        "-34448.28", "DLA0000006/BDWTEIA/1,000/34.44828/Buy/ BlackRock Asset Management", "TRAD");
    parkBankOperation("-3.00", "Korrigeerimine", "ADJT");

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(2, 2));
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntriesBefore(TUK75, AFTER_EVERY_BOOKING))
        .isEqualTo(2);
    assertThat(balance(INTEREST_INCOME)).isEqualByComparingTo(new BigDecimal("-2.00"));
    assertThat(balance(SystemAccount.BANK_ADJUSTMENT)).isEqualByComparingTo(new BigDecimal("3.00"));
    assertThat(balance(UNCLASSIFIED_BANK_ENTRY)).isEqualByComparingTo(new BigDecimal("34441.28"));
  }

  private void parkFromCounterparty(
      String amount,
      @Nullable String counterpartyName,
      String counterpartyIban,
      String remittanceInformation,
      String subFamilyCode) {
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal(amount),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            counterpartyName, counterpartyIban, remittanceInformation, subFamilyCode));
  }

  private void parkBankOperation(
      String amount, String remittanceInformation, String subFamilyCode) {
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal(amount),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            null, null, remittanceInformation, subFamilyCode));
  }

  private BigDecimal instrumentBalance(SystemAccount systemAccount) {
    return navLedgerRepository.getSystemAccountBalance(
        systemAccount.getAccountName(TUK75, SUBFUND_ISIN));
  }

  private BigDecimal balance(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount, TulevaFund.TUK75).getBalance();
  }
}
