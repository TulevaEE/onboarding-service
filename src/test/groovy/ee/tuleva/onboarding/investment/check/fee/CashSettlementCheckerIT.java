package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.banking.seb.processor.PensionFundStatementProcessor;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry.CounterPartyDetails;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SebIntegrationTest
class CashSettlementCheckerIT {

  private static final String TUK75_IBAN = "EE001234567890123475";
  private static final BankAccount TUK75_ACCOUNT =
      new BankAccount(TUK75_IBAN, FUND_INVESTMENT_EUR, TUK75, "gw-test");
  private static final String MANAGER_NAME = "Tuleva Fondid AS";
  private static final String MANAGER_IBAN = "EE001234567890123488";
  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate LAST_DAY_OF_APRIL = LocalDate.of(2026, 4, 30);
  private static final LocalDate PAYMENT_DAY = LocalDate.of(2026, 5, 12);
  private static final Instant PAYMENT_BOOKED = Instant.parse("2026-05-12T10:00:00Z");
  private static final LocalDate WINDOW_ELAPSED = LocalDate.of(2026, 6, 3);
  private static final BigDecimal SETTLED = new BigDecimal("1234.56");

  @Autowired private CashSettlementChecker checker;
  @Autowired private PensionFundStatementProcessor processor;
  @Autowired private NavFeeAccrualLedger navFeeAccrualLedger;

  @Test
  void aPensionFundsFeePaymentBesideAnotherPaymentToTheManagerPassesAgainstItsSettlement() {
    navFeeAccrualLedger.settleFeeAccrual(TUK75, LAST_DAY_OF_APRIL, MANAGEMENT_FEE_ACCRUAL, SETTLED);

    processor.process(
        statementWith(
            managerDebit("fee-04", SETTLED.negate(), "TUK75 valitsemistasu 04/2026"),
            managerDebit("other-04", new BigDecimal("-50.00"), "BR tasud TUK75")),
        TUK75_ACCOUNT);

    assertThat(checker.check(TUK75, APRIL, WINDOW_ELAPSED))
        .extracting(FeeCheckFinding::fund, FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(tuple(TUK75, PASS, List.of()));
  }

  private static BankStatement statementWith(BankStatementEntry... entries) {
    return new BankStatement(
        BankStatementType.HISTORIC_STATEMENT,
        new BankStatementAccount(TUK75_IBAN, "Tuleva Maailma Aktsiate Pensionifond", "14118923"),
        List.of(),
        List.of(entries),
        new StatementPeriod(PAYMENT_DAY, PAYMENT_DAY));
  }

  private static BankStatementEntry managerDebit(
      String externalId, BigDecimal amount, String remittanceInformation) {
    return new BankStatementEntry(
        new CounterPartyDetails(MANAGER_NAME, MANAGER_IBAN, null),
        amount,
        "EUR",
        TransactionType.DEBIT,
        remittanceInformation,
        externalId,
        null,
        "ICDT",
        PAYMENT_BOOKED,
        true);
  }
}
