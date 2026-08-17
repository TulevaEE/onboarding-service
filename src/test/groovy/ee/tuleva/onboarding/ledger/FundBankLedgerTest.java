package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.BANK_FEE;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FundBankLedgerTest {

  private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 5, 28);

  @Autowired LedgerService ledgerService;
  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired FundBankLedger fundBankLedger;

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void recordManagementFeePayment_createsCorrectLedgerEntries() {
    var feeAmount = new BigDecimal("742.34");
    var externalReference = randomUUID();
    var description = "Valitsemistasu 02.-28.02.26";

    var transaction =
        fundBankLedger.recordManagementFeePayment(
            TKF100, feeAmount, externalReference, description, BOOKING_DATE);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("MANAGEMENT_FEE_PAYMENT");
    assertThat(transaction.getMetadata().get("description")).isEqualTo(description);
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(MANAGEMENT_FEE, TKF100).getBalance())
        .isEqualByComparingTo(feeAmount);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TKF100).getBalance())
        .isEqualByComparingTo(feeAmount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordBankFee_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("-1.50");
    var externalReference = randomUUID();

    var transaction =
        fundBankLedger.recordBankFee(
            TKF100, amount, externalReference, INCOMING_PAYMENTS_CLEARING, BOOKING_DATE);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("BANK_FEE");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(SystemAccount.BANK_FEE, TKF100).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100).getBalance())
        .isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordInterestReceived_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("5.00");
    var externalReference = randomUUID();

    var transaction =
        fundBankLedger.recordInterestReceived(
            TKF100, amount, externalReference, INCOMING_PAYMENTS_CLEARING, BOOKING_DATE);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("INTEREST_RECEIVED");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(INTEREST_INCOME, TKF100).getBalance())
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordManagementFeeRebate_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("4370.58");
    var externalReference = randomUUID();
    var description = "Management fee kickback VP68168 02/2026";

    var transaction =
        fundBankLedger.recordManagementFeeRebate(
            TKF100,
            amount,
            externalReference,
            FUND_INVESTMENT_CASH_CLEARING,
            BOOKING_DATE,
            description);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("MANAGEMENT_FEE_REBATE");
    assertThat(transaction.getMetadata().get("description")).isEqualTo(description);
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TKF100).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(MANAGEMENT_FEE_REBATE, TKF100).getBalance())
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordBankAdjustment_createsCorrectLedgerEntries() {
    var amount = new BigDecimal("0.500");
    var externalReference = randomUUID();

    var transaction =
        fundBankLedger.recordBankAdjustment(
            TKF100, amount, externalReference, INCOMING_PAYMENTS_CLEARING, BOOKING_DATE);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("BANK_ADJUSTMENT");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(BANK_ADJUSTMENT, TKF100).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100).getBalance())
        .isEqualByComparingTo(amount);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordTradeSettlement_createsCorrectFourEntryTransaction() {
    var amount = new BigDecimal("-209025.86");
    var units = new BigDecimal("11704.00000");
    var externalReference = randomUUID();
    var isin = "LU1291102447";

    var transaction =
        fundBankLedger.recordTradeSettlement(
            TKF100,
            amount,
            units,
            externalReference,
            FUND_INVESTMENT_CASH_CLEARING,
            isin,
            "EJAP",
            "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
            BOOKING_DATE);

    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("TRADE_SETTLEMENT");
    assertThat(transaction.getMetadata().get("instrument")).isEqualTo(isin);
    assertThat(transaction.getMetadata().get("ticker")).isEqualTo("EJAP");
    assertThat(transaction.getMetadata().get("displayName"))
        .isEqualTo("BNP Paribas Easy MSCI Japan Min TE UCITS ETF");
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getEntries()).hasSize(4);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TKF100).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getInstrumentAccount(TRADE_CASH_SETTLEMENT, TKF100, isin).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getInstrumentAccount(TRADE_UNIT_SETTLEMENT, TKF100, isin).getBalance())
        .isEqualByComparingTo(units.negate());
    assertThat(getInstrumentAccount(SECURITIES_CUSTODY, TKF100, isin).getBalance())
        .isEqualByComparingTo(units);
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordTradeSettlement_createsPerInstrumentAccounts() {
    var amount1 = new BigDecimal("-209025.86");
    var units1 = new BigDecimal("11704.00000");
    var amount2 = new BigDecimal("-995467.50");
    var units2 = new BigDecimal("19422.00000");
    var isin1 = "LU1291102447";
    var isin2 = "IE00BJZ2DC62";

    fundBankLedger.recordTradeSettlement(
        TKF100,
        amount1,
        units1,
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        isin1,
        "EJAP",
        "BNP Japan",
        BOOKING_DATE);
    fundBankLedger.recordTradeSettlement(
        TKF100,
        amount2,
        units2,
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        isin2,
        "XRSM",
        "Xtrackers USA",
        BOOKING_DATE);

    assertThat(getInstrumentAccount(TRADE_CASH_SETTLEMENT, TKF100, isin1).getBalance())
        .isEqualByComparingTo(amount1.negate());
    assertThat(getInstrumentAccount(TRADE_CASH_SETTLEMENT, TKF100, isin2).getBalance())
        .isEqualByComparingTo(amount2.negate());
    assertThat(getInstrumentAccount(TRADE_UNIT_SETTLEMENT, TKF100, isin1).getBalance())
        .isEqualByComparingTo(units1.negate());
    assertThat(getInstrumentAccount(TRADE_UNIT_SETTLEMENT, TKF100, isin2).getBalance())
        .isEqualByComparingTo(units2.negate());
  }

  @Test
  void bankOperations_areQualifiedByFund() {
    var isin = "IE00BFG1TM61";

    fundBankLedger.recordBankFee(
        TUK75, new BigDecimal("-2.50"), randomUUID(), FUND_INVESTMENT_CASH_CLEARING, BOOKING_DATE);
    var tradeSettlement =
        fundBankLedger.recordTradeSettlement(
            TUK75,
            new BigDecimal("-999000.00"),
            new BigDecimal("29000.00000"),
            randomUUID(),
            FUND_INVESTMENT_CASH_CLEARING,
            isin,
            "0P000152G5",
            "iShares Developed World Screened Index Fund",
            BOOKING_DATE);

    assertThat(getSystemAccount(SystemAccount.BANK_FEE, TUK75).getName())
        .isEqualTo("BANK_FEE:TUK75");
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getName())
        .isEqualTo("FUND_INVESTMENT_CASH_CLEARING:TUK75");
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(new BigDecimal("-999002.50"));
    assertThat(getInstrumentAccount(SECURITIES_CUSTODY, TUK75, isin).getName())
        .isEqualTo("SECURITIES_CUSTODY:TUK75:" + isin);
    assertThat(getSystemAccount(SystemAccount.BANK_FEE, TKF100).getBalance())
        .isEqualByComparingTo(ZERO);
    verifyDoubleEntry(tradeSettlement);
  }

  @Test
  void hasLedgerEntry_checksReferenceAndTransactionType() {
    var externalReference = randomUUID();
    fundBankLedger.recordBankFee(
        TKF100,
        new BigDecimal("-1.50"),
        externalReference,
        INCOMING_PAYMENTS_CLEARING,
        BOOKING_DATE);

    assertThat(fundBankLedger.hasLedgerEntry(externalReference, BANK_FEE)).isTrue();
    assertThat(fundBankLedger.hasLedgerEntry(randomUUID(), BANK_FEE)).isFalse();
    assertThat(
            fundBankLedger.hasLedgerEntry(
                externalReference, LedgerTransaction.TransactionType.INTEREST_RECEIVED))
        .isFalse();
  }

  @Test
  void recordManagementFeePayment_usesBookingDateWhenDifferentFromToday() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2025-10-02T12:00:00Z"), ZoneId.of("UTC")));

    var transaction =
        fundBankLedger.recordManagementFeePayment(
            TKF100,
            new BigDecimal("742.34"),
            randomUUID(),
            "Valitsemistasu",
            LocalDate.of(2025, 10, 1));

    assertThat(transaction.getTransactionDate()).isBefore(Instant.parse("2025-10-02T00:00:00Z"));
  }

  @Test
  void recordManagementFeePayment_usesClockWhenSameDay() {
    var clockInstant = Instant.parse("2025-10-01T14:54:35Z");
    ClockHolder.setClock(Clock.fixed(clockInstant, ZoneId.of("UTC")));

    var transaction =
        fundBankLedger.recordManagementFeePayment(
            TKF100,
            new BigDecimal("742.34"),
            randomUUID(),
            "Valitsemistasu",
            LocalDate.of(2025, 10, 1));

    assertThat(transaction.getTransactionDate()).isEqualTo(clockInstant);
  }

  @Test
  void recordRegistrarContribution_movesCashAgainstRegistrarSettlement() {
    var amount = new BigDecimal("1000000.00");
    var externalReference = randomUUID();

    var transaction =
        fundBankLedger.recordRegistrarContribution(
            TUK75, amount, externalReference, BOOKING_DATE, "EPIS osakute laekumine");

    assertThat(transaction.getTransactionType())
        .isEqualTo(LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION);
    assertThat(transaction.getExternalReference()).isEqualTo(externalReference);
    assertThat(transaction.getMetadata().get("operationType")).isEqualTo("REGISTRAR_CONTRIBUTION");
    assertThat(transaction.getMetadata().get("description")).isEqualTo("EPIS osakute laekumine");
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(REGISTRAR_CASH_SETTLEMENT, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(getSystemAccount(REGISTRAR_CASH_SETTLEMENT, TUK75).getName())
        .isEqualTo("REGISTRAR_CASH_SETTLEMENT:TUK75");
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordRegistrarPayout_movesCashOutAgainstRegistrarSettlement() {
    var amount = new BigDecimal("-250000.00");
    var externalReference = randomUUID();

    var transaction =
        fundBankLedger.recordRegistrarPayout(
            TUK75, amount, externalReference, BOOKING_DATE, "EPIS väljamaksed");

    assertThat(transaction.getTransactionType())
        .isEqualTo(LedgerTransaction.TransactionType.REGISTRAR_PAYOUT);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(REGISTRAR_CASH_SETTLEMENT, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordUnclassifiedBankEntry_booksCashCorrectlyAndParksCounterLegInSuspense() {
    var amount = new BigDecimal("99.99");
    var externalReference = randomUUID();
    var details =
        new FundBankLedger.UnclassifiedEntryDetails(
            "Mystery Counterparty OU", "EE001234567890123499", "Selgituseta laekumine", "OTHR");

    var transaction =
        fundBankLedger.recordUnclassifiedBankEntry(
            TUK75, amount, externalReference, BOOKING_DATE, details);

    assertThat(transaction.getTransactionType())
        .isEqualTo(LedgerTransaction.TransactionType.UNCLASSIFIED_BANK_ENTRY);
    assertThat(transaction.getMetadata())
        .containsEntry("counterpartyName", "Mystery Counterparty OU")
        .containsEntry("counterpartyIban", "EE001234567890123499")
        .containsEntry("description", "Selgituseta laekumine")
        .containsEntry("subFamilyCode", "OTHR");
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(UNCLASSIFIED_BANK_ENTRY, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());
    verifyDoubleEntry(transaction);
  }

  @Test
  void recordOpeningBalance_postsAtEndOfHistoricDateWithDeterministicReference() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-17T12:00:00Z"), ZoneId.of("UTC")));
    var amount = new BigDecimal("500000.00");
    var asOfDate = LocalDate.of(2026, 1, 31);

    var transaction = fundBankLedger.recordOpeningBalance(TUK75, amount, asOfDate);

    assertThat(transaction.getTransactionType())
        .isEqualTo(LedgerTransaction.TransactionType.OPENING_BALANCE);
    assertThat(transaction.getTransactionDate())
        .isBefore(Instant.parse("2026-02-01T00:00:00Z"))
        .isAfter(Instant.parse("2026-01-31T00:00:00Z"));
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(REGISTRAR_CASH_SETTLEMENT, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());

    var replay = fundBankLedger.recordOpeningBalance(TUK75, amount, asOfDate);

    assertThat(replay).isEqualTo(transaction);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
  }

  @Test
  void existsForExternalReference_isClassificationIndependent() {
    var externalReference = randomUUID();
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("10.00"),
        externalReference,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(null, null, null, "OTHR"));

    assertThat(fundBankLedger.existsForExternalReference(externalReference)).isTrue();
    assertThat(fundBankLedger.existsForExternalReference(randomUUID())).isFalse();
  }

  @Test
  void countUnresolvedUnclassifiedEntries_countsEntriesNotNetBalance() {
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("100.00"),
        randomUUID(),
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(null, null, "credit", "OTHR"));
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("-100.00"),
        randomUUID(),
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(null, null, "offsetting debit", "OTHR"));

    assertThat(getSystemAccount(UNCLASSIFIED_BANK_ENTRY, TUK75).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isEqualTo(2);
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TKF100)).isZero();
  }

  @Test
  void countUnresolvedUnclassifiedEntries_treatsSameReferenceResolutionAsResolved() {
    var externalReference = randomUUID();
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        new BigDecimal("1000.00"),
        externalReference,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "AS Pensionikeskus", "EE001234567890123477", "laekumine", null));

    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isEqualTo(1);

    fundBankLedger.recordRegistrarContribution(
        TUK75, ZERO, externalReference, BOOKING_DATE, "reclassified from suspense");

    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isZero();
  }

  @Test
  void reclassifySuspenseEntry_movesCounterLegToRegistrarWithoutTouchingCash() {
    var amount = new BigDecimal("1000000.00");
    var externalReference = randomUUID();
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        amount,
        externalReference,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "AS Pensionikeskus", "EE001234567890123477", "laekumine", null));

    var correction =
        fundBankLedger.reclassifySuspenseEntry(
            TUK75,
            amount,
            externalReference,
            LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION,
            BOOKING_DATE);

    assertThat(correction.getTransactionType())
        .isEqualTo(LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION);
    assertThat(correction.getExternalReference()).isEqualTo(externalReference);
    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(UNCLASSIFIED_BANK_ENTRY, TUK75).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(REGISTRAR_CASH_SETTLEMENT, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isZero();

    var replay =
        fundBankLedger.reclassifySuspenseEntry(
            TUK75,
            amount,
            externalReference,
            LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION,
            BOOKING_DATE);

    assertThat(replay).isEqualTo(correction);
    assertThat(getSystemAccount(REGISTRAR_CASH_SETTLEMENT, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());
  }

  @Test
  void reclassifySuspenseEntry_toManagementFeeMovesCounterLegToExpense() {
    var amount = new BigDecimal("-742.34");
    var externalReference = randomUUID();
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK75,
        amount,
        externalReference,
        BOOKING_DATE,
        new FundBankLedger.UnclassifiedEntryDetails(
            "Tuleva Fondid AS", "EE001234567890123488", "Valitsemistasu 02/2026", null));

    fundBankLedger.reclassifySuspenseEntry(
        TUK75,
        amount,
        externalReference,
        LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT,
        BOOKING_DATE);

    assertThat(getSystemAccount(FUND_INVESTMENT_CASH_CLEARING, TUK75).getBalance())
        .isEqualByComparingTo(amount);
    assertThat(getSystemAccount(UNCLASSIFIED_BANK_ENTRY, TUK75).getBalance())
        .isEqualByComparingTo(ZERO);
    assertThat(getSystemAccount(MANAGEMENT_FEE, TUK75).getBalance())
        .isEqualByComparingTo(amount.negate());
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerService.getSystemAccount(systemAccount, fund);
  }

  private LedgerAccount getInstrumentAccount(
      SystemAccount systemAccount, TulevaFund fund, String isin) {
    return ledgerAccountService
        .findSystemAccountByName(
            systemAccount.getAccountName(fund, isin),
            systemAccount.getAccountType(),
            systemAccount.getAssetType())
        .orElseThrow();
  }

  private static void verifyDoubleEntry(LedgerTransaction transaction) {
    List<LedgerEntry> entries = transaction.getEntries();
    assertThat(entries.size()).isGreaterThan(1);

    BigDecimal totalDebits =
        entries.stream()
            .map(LedgerEntry::getAmount)
            .filter(amount -> amount.compareTo(ZERO) > 0)
            .reduce(ZERO, BigDecimal::add);

    BigDecimal totalCredits =
        entries.stream()
            .filter(entry -> entry.getAmount().compareTo(ZERO) < 0)
            .map(entry -> entry.getAmount().abs())
            .reduce(ZERO, BigDecimal::add);

    assertThat(totalDebits.compareTo(totalCredits)).isEqualTo(0);
  }
}
