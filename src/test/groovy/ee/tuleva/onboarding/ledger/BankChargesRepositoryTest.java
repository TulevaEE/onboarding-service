package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INTEREST_INCOME;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  LedgerAccountService.class,
  LedgerTransactionService.class,
  UserUnitBalanceGuard.class,
  FundBankLedger.class,
  BankChargesRepository.class,
  ClockConfig.class
})
class BankChargesRepositoryTest {

  private static final LocalDate MARCH_FIRST = LocalDate.of(2026, 3, 1);
  private static final LocalDate MARCH_LAST = LocalDate.of(2026, 3, 31);

  @Autowired FundBankLedger fundBankLedger;
  @Autowired BankChargesRepository repository;
  @Autowired JdbcClient jdbcClient;

  @Test
  void addsUpTheBankFeesNetOfRefundsAndTheOverdraftInterestTheFundPaid() {
    bankStatementFee(TKF100, "-0.18", LocalDate.of(2026, 3, 5));
    bankStatementFee(TKF100, "0.06", LocalDate.of(2026, 3, 20));
    bankStatementInterest(TKF100, "-4.20", MARCH_LAST);

    assertThat(repository.chargedBetween(TKF100, MARCH_FIRST, MARCH_LAST))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(new BankCharges(new BigDecimal("0.12"), new BigDecimal("4.20")));
  }

  @Test
  void interestTheFundEarnedIsNotACharge() {
    bankStatementInterest(TKF100, "12.34", MARCH_LAST);

    assertThat(repository.chargedBetween(TKF100, MARCH_FIRST, MARCH_LAST).total())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void aManualEntryOnTheInterestAccountIsNotOverdraftInterest() {
    new BankStatementLedgerLines(jdbcClient)
        .book(
            TKF100, "JOURNAL_ENTRY", LocalDateTime.of(2026, 3, 10, 8, 0), INTEREST_INCOME, "7.00");

    assertThat(repository.chargedBetween(TKF100, MARCH_FIRST, MARCH_LAST).total())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void countsOnlyTheFundsOwnFeesBookedOnTheDaysOfTheWindow() {
    bankStatementFee(TUK75, "-5.00", LocalDate.of(2026, 3, 5));
    bankStatementFee(TKF100, "-1.00", LocalDate.of(2026, 2, 28));
    bankStatementFee(TKF100, "-0.06", MARCH_FIRST);
    bankStatementFee(TKF100, "-0.12", MARCH_LAST);
    bankStatementFee(TKF100, "-2.00", LocalDate.of(2026, 4, 1));

    assertThat(repository.chargedBetween(TKF100, MARCH_FIRST, MARCH_LAST).bankFees())
        .isEqualByComparingTo("0.18");
  }

  @Test
  void theLedgerHoldsAFundsBankStatementsFromTheMonthOfItsFirstBankFeeOrInterestLine() {
    registrarContribution(TUK75, LocalDate.of(2026, 1, 20));
    bankStatementInterest(TUK75, "0.50", LocalDate.of(2026, 2, 27));
    bankStatementFee(TUK75, "-0.06", LocalDate.of(2026, 3, 2));

    assertThat(repository.statementsInLedgerFrom(TUK75)).contains(LocalDate.of(2026, 2, 1));
  }

  @Test
  void aLineBookedOnTheLastDayOfAMonthBelongsToThatMonth() {
    bankStatementInterest(TUK75, "0.50", LocalDate.of(2026, 1, 31));

    assertThat(repository.statementsInLedgerFrom(TUK75)).contains(LocalDate.of(2026, 1, 1));
  }

  @Test
  void aFundWithoutBankFeeOrInterestLinesHasNoStatementsInTheLedger() {
    registrarContribution(TUK75, LocalDate.of(2026, 1, 20));

    assertThat(repository.statementsInLedgerFrom(TUK75)).isEmpty();
  }

  private void bankStatementFee(TulevaFund fund, String statementAmount, LocalDate bookingDate) {
    fundBankLedger.recordBankFee(
        fund,
        new BigDecimal(statementAmount),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        bookingDate);
  }

  private void bankStatementInterest(
      TulevaFund fund, String statementAmount, LocalDate bookingDate) {
    fundBankLedger.recordInterestReceived(
        fund,
        new BigDecimal(statementAmount),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        bookingDate);
  }

  private void registrarContribution(TulevaFund fund, LocalDate bookingDate) {
    fundBankLedger.recordRegistrarContribution(
        fund, new BigDecimal("100.00"), randomUUID(), bookingDate, "Synthetic contribution");
  }
}
