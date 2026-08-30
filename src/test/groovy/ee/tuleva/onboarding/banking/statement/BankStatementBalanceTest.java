package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.iso20022.camt052.BalanceType12Code;
import ee.tuleva.onboarding.banking.iso20022.camt052.CreditDebitCode;
import ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BankStatementBalanceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 3, 13);

  @Test
  void from_creditBalance_isPositive() {
    var balance =
        Camt052Fixtures.balance(BalanceType12Code.CLBD, DATE, "1000.00", CreditDebitCode.CRDT);

    var result = BankStatementBalance.from(balance);

    assertThat(result).isEqualTo(new BankStatementBalance(CLOSE, DATE, new BigDecimal("1000.00")));
  }

  @Test
  void from_debitBalance_isNegated() {
    var balance =
        Camt052Fixtures.balance(BalanceType12Code.OPBD, DATE, "500.00", CreditDebitCode.DBIT);

    var result = BankStatementBalance.from(balance);

    assertThat(result).isEqualTo(new BankStatementBalance(OPEN, DATE, new BigDecimal("-500.000")));
  }

  @Test
  void fromBalanceCode_mapsOpeningBookedBalance() {
    assertThat(StatementBalanceType.fromBalanceCode(BalanceType12Code.OPBD)).isEqualTo(OPEN);
  }

  @Test
  void fromBalanceCode_mapsClosingBookedBalance() {
    assertThat(StatementBalanceType.fromBalanceCode(BalanceType12Code.CLBD)).isEqualTo(CLOSE);
  }
}
