package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.systemAccountWithBalance;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.swedbank.statement.BankStatementAccount;
import ee.tuleva.onboarding.swedbank.statement.BankStatementBalance;
import ee.tuleva.onboarding.swedbank.statement.BankStatementEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliatorTest {

  @Mock private LedgerService ledgerService;

  @InjectMocks private Reconciliator reconciliator;

  @Test
  void reconcile_shouldSucceed_whenBalancesMatch() {
    // Given
    BigDecimal matchingBalance = new BigDecimal("1000.00");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance closingBalance =
        new BankStatementBalance(CLOSE, balanceDate, matchingBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    List<BankStatementEntry> entries = List.of();
    BankStatement bankStatement =
        new BankStatement(
            HISTORIC_STATEMENT, account, List.of(closingBalance), entries, Instant.now());

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(matchingBalance, toEstonianTime(balanceDate.minusDays(1)));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);

    // When & Then
    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldThrowException_whenBalancesDoNotMatch() {
    // Given
    BigDecimal bankBalance = new BigDecimal("1000.00");
    BigDecimal ledgerBalance = new BigDecimal("999.99");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance closingBalance = new BankStatementBalance(CLOSE, balanceDate, bankBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE987700771001802057", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(
            HISTORIC_STATEMENT, account, List.of(closingBalance), List.of(), Instant.now());

    // Create LedgerAccount with different balance, dated before the balance check
    LedgerAccount ledgerAccount =
        systemAccountWithBalance(ledgerBalance, toEstonianTime(balanceDate.minusDays(1)));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);

    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldThrowException_whenNoClosingBalance() {
    // Given
    BankStatementBalance openingBalance =
        new BankStatementBalance(OPEN, LocalDate.now(), new BigDecimal("500.00"));
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(
            HISTORIC_STATEMENT,
            account,
            List.of(openingBalance), // Only opening balance, no closing balance
            List.of(),
            Instant.now());
    // When & Then
    assertThrows(NoSuchElementException.class, () -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldHandleMultipleBalances_andUseClosingBalance() {
    // Given
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
            HISTORIC_STATEMENT,
            account,
            List.of(openingBalance, closingBalance), // Both balances
            List.of(),
            Instant.now());

    // Create LedgerAccount with the matching closing balance
    LedgerAccount ledgerAccount =
        systemAccountWithBalance(matchingBalance, toEstonianTime(balanceDate.minusDays(1)));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);

    // When & Then
    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }

  private static Instant toEstonianTime(LocalDate date) {
    return date.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
  }
}
