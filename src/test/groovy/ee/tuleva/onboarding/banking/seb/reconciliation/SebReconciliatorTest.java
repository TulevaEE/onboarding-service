package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.systemAccountWithBalance;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementBalance;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebReconciliatorTest {

  private static final Instant RECONCILIATION_TIME = Instant.parse("2024-01-16T01:05:00Z");

  @Mock private LedgerService ledgerService;
  @Mock private SebAccountConfiguration sebAccountConfiguration;
  @Mock private Clock clock;

  @InjectMocks private SebReconciliator reconciliator;

  @Test
  void reconcile_shouldSucceed_whenBalancesMatch() {
    BigDecimal matchingBalance = new BigDecimal("1000.00");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance closingBalance =
        new BankStatementBalance(CLOSE, balanceDate, matchingBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(HISTORIC_STATEMENT, account, List.of(closingBalance), List.of());

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(matchingBalance, RECONCILIATION_TIME.minusSeconds(60));

    when(clock.instant()).thenReturn(RECONCILIATION_TIME);
    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE123456789012345678")).thenReturn(DEPOSIT_EUR);

    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldThrowException_whenBalancesDoNotMatch() {
    BigDecimal bankBalance = new BigDecimal("1000.00");
    BigDecimal ledgerBalance = new BigDecimal("999.99");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance closingBalance = new BankStatementBalance(CLOSE, balanceDate, bankBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE987700771001802057", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(HISTORIC_STATEMENT, account, List.of(closingBalance), List.of());

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(ledgerBalance, RECONCILIATION_TIME.minusSeconds(60));

    when(clock.instant()).thenReturn(RECONCILIATION_TIME);
    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE987700771001802057")).thenReturn(DEPOSIT_EUR);

    assertThrows(IllegalStateException.class, () -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldThrowException_whenNoClosingBalance() {
    BankStatementBalance openingBalance =
        new BankStatementBalance(OPEN, LocalDate.now(), new BigDecimal("500.00"));
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(HISTORIC_STATEMENT, account, List.of(openingBalance), List.of());

    assertThrows(NoSuchElementException.class, () -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldHandleMultipleBalances_andUseClosingBalance() {
    BigDecimal matchingBalance = new BigDecimal("2000.00");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance openingBalance =
        new BankStatementBalance(OPEN, balanceDate, new BigDecimal("1500.00"));
    BankStatementBalance closingBalance =
        new BankStatementBalance(CLOSE, balanceDate, matchingBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(
            HISTORIC_STATEMENT, account, List.of(openingBalance, closingBalance), List.of());

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(matchingBalance, RECONCILIATION_TIME.minusSeconds(60));

    when(clock.instant()).thenReturn(RECONCILIATION_TIME);
    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE123456789012345678")).thenReturn(DEPOSIT_EUR);

    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }
}
