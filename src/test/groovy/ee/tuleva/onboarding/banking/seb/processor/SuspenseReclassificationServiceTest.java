package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.REGISTRAR_CASH_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNCLASSIFIED_BANK_ENTRY;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SebIntegrationTest
class SuspenseReclassificationServiceTest {

  private static final String REGISTRAR_IBAN = "EE001234567890123477";
  private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 2, 10);

  @Autowired private SuspenseReclassificationService service;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private LedgerService ledgerService;

  @DynamicPropertySource
  static void registrarIbans(DynamicPropertyRegistry registry) {
    registry.add("seb-gateway.registrar-ibans", () -> REGISTRAR_IBAN);
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
