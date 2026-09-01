package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.REGISTRAR_CASH_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNCLASSIFIED_BANK_ENTRY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SebIntegrationTest
class SuspenseReclassificationServiceTest {

  private static final String REGISTRAR_IBAN = "EE001234567890123477";
  private static final String OWN_ACCOUNT_IBAN = "EE001234567890123490";
  private static final String BANK_FEE_IBAN = "EE001234567890123491";
  private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 2, 10);

  @Autowired private SuspenseReclassificationService service;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private LedgerService ledgerService;

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
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isZero();
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
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isEqualTo(1);

    var replay = service.reclassify(TUK75);

    assertThat(replay).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    assertThat(balance(REGISTRAR_CASH_SETTLEMENT))
        .isEqualByComparingTo(new BigDecimal("-750000.00"));
  }

  private BigDecimal balance(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount, TulevaFund.TUK75).getBalance();
  }
}
