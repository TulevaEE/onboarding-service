package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebStatementRouterTest {

  private static final String DEPOSIT_IBAN = "EE001234567890123456";
  private static final String UNKNOWN_IBAN = "EE112233445566778899";

  @Mock private BankAccounts bankAccounts;
  @Mock private SavingsFundStatementProcessor savingsFundStatementProcessor;
  @Mock private PensionFundStatementProcessor pensionFundStatementProcessor;

  @InjectMocks private SebStatementRouter router;

  @Test
  void route_dispatchesSavingsFundStatementToSavingsFundProcessor() {
    var account = new BankAccount(DEPOSIT_IBAN, DEPOSIT_EUR, TKF100, "gw-test");
    var statement = statementFor(DEPOSIT_IBAN);
    when(bankAccounts.find(DEPOSIT_IBAN)).thenReturn(Optional.of(account));

    router.route(statement);

    verify(savingsFundStatementProcessor).process(statement, account);
    verifyNoInteractions(pensionFundStatementProcessor);
  }

  @Test
  void route_dispatchesPensionFundStatementToPensionFundProcessor() {
    var pensionIban = "EE001234567890123475";
    var account = new BankAccount(pensionIban, FUND_INVESTMENT_EUR, TUK75, "gw-test");
    var statement = statementFor(pensionIban);
    when(bankAccounts.find(pensionIban)).thenReturn(Optional.of(account));

    router.route(statement);

    verify(pensionFundStatementProcessor).process(statement, account);
    verifyNoInteractions(savingsFundStatementProcessor);
  }

  @Test
  void route_throwsOnUnknownAccount() {
    var statement = statementFor(UNKNOWN_IBAN);
    when(bankAccounts.find(UNKNOWN_IBAN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> router.route(statement))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(UNKNOWN_IBAN);

    verifyNoInteractions(savingsFundStatementProcessor);
    verifyNoInteractions(pensionFundStatementProcessor);
  }

  private BankStatement statementFor(String iban) {
    return new BankStatement(
        HISTORIC_STATEMENT,
        new BankStatementAccount(iban, "Tuleva Fondid AS", "14118923"),
        List.of(),
        List.of());
  }
}
