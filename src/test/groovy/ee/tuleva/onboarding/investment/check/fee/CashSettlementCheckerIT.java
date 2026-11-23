package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
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
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeMonthBalance;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
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
  private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
  private static final LocalDate NOVEMBER_16 = LocalDate.of(2026, 11, 16);
  private static final LocalDate DECEMBER_1 = LocalDate.of(2026, 12, 1);
  private static final LocalDate DECEMBER_2 = LocalDate.of(2026, 12, 2);
  private static final Instant DEBITS_BOOKED = Instant.parse("2026-12-01T10:00:00Z");
  private static final BigDecimal NOVEMBER_FEE = new BigDecimal("1234.56");

  @Autowired private CashSettlementChecker checker;
  @Autowired private PensionFundStatementProcessor processor;
  @Autowired private FeeAccrualRepository feeAccrualRepository;
  @Autowired private OutstandingFees outstandingFees;
  @Autowired private FeeChargedToFundPolicy feeChargedToFundPolicy;

  @Test
  void aPensionFundsFeePaymentBesideAnotherPaymentToTheManagerClearsTheMonthItPays() {
    feeAccrualRepository.save(
        new FeeAccrual(
            null,
            TUK75,
            MANAGEMENT,
            NOVEMBER_16,
            NOVEMBER,
            new BigDecimal("1000000000"),
            new BigDecimal("0.0045"),
            NOVEMBER_FEE,
            365));

    processor.process(
        statementWith(
            managerDebit("fee-11", NOVEMBER_FEE.negate(), "TUK75 valitsemistasu 11/2026"),
            managerDebit("other-11", new BigDecimal("-50.00"), "BR tasud TUK75")),
        TUK75_ACCOUNT);

    assertThat(
            outstandingFees.monthsOn(
                feeChargedToFundPolicy.resolverFor(TUK75, MANAGEMENT), DECEMBER_1))
        .filteredOn(month -> month.feeMonth().equals(NOVEMBER))
        .extracting(FeeMonthBalance::paid, FeeMonthBalance::owed, FeeMonthBalance::clearedOn)
        .containsExactly(tuple(NOVEMBER_FEE, ZERO, DECEMBER_1));
    assertThat(checker.check(TUK75, DECEMBER_2))
        .extracting(FeeCheckFinding::fund, FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(tuple(TUK75, PASS, List.of()));
  }

  private static BankStatement statementWith(BankStatementEntry... entries) {
    return new BankStatement(
        BankStatementType.HISTORIC_STATEMENT,
        new BankStatementAccount(TUK75_IBAN, "Tuleva Maailma Aktsiate Pensionifond", "14118923"),
        List.of(),
        List.of(entries),
        new StatementPeriod(DECEMBER_1, DECEMBER_1));
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
        DEBITS_BOOKED,
        true);
  }
}
