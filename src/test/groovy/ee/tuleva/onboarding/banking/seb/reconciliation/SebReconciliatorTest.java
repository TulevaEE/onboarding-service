package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.systemAccountWithBalance;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.systemAccountWithEntries;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementBalance;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerAccountFixture.EntryFixture;
import ee.tuleva.onboarding.ledger.LedgerService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SebReconciliatorTest {

  @Mock private LedgerService ledgerService;
  @Mock private SebAccountConfiguration sebAccountConfiguration;
  @Mock private ApplicationEventPublisher eventPublisher;

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
        systemAccountWithBalance(matchingBalance, Instant.parse("2024-01-15T12:00:00Z"));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount(), TKF100))
        .thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE123456789012345678")).thenReturn(DEPOSIT_EUR);

    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldPublishMatchedEvent_whenBalancesMatch() {
    BigDecimal matchingBalance = new BigDecimal("1000.00");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance closingBalance =
        new BankStatementBalance(CLOSE, balanceDate, matchingBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(HISTORIC_STATEMENT, account, List.of(closingBalance), List.of());

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(matchingBalance, Instant.parse("2024-01-15T12:00:00Z"));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount(), TKF100))
        .thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE123456789012345678")).thenReturn(DEPOSIT_EUR);

    reconciliator.reconcile(bankStatement);

    verify(eventPublisher)
        .publishEvent(
            new ReconciliationCompletedEvent(DEPOSIT_EUR, matchingBalance, matchingBalance, true));
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
        systemAccountWithBalance(ledgerBalance, Instant.parse("2024-01-15T12:00:00Z"));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount(), TKF100))
        .thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE987700771001802057")).thenReturn(DEPOSIT_EUR);

    assertThrows(IllegalStateException.class, () -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void reconcile_shouldPublishMismatchedEvent_whenBalancesDoNotMatch() {
    BigDecimal bankBalance = new BigDecimal("1000.00");
    BigDecimal ledgerBalance = new BigDecimal("999.99");
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);

    BankStatementBalance closingBalance = new BankStatementBalance(CLOSE, balanceDate, bankBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE987700771001802057", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(HISTORIC_STATEMENT, account, List.of(closingBalance), List.of());

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(ledgerBalance, Instant.parse("2024-01-15T12:00:00Z"));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount(), TKF100))
        .thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE987700771001802057")).thenReturn(DEPOSIT_EUR);

    assertThrows(IllegalStateException.class, () -> reconciliator.reconcile(bankStatement));

    verify(eventPublisher)
        .publishEvent(
            new ReconciliationCompletedEvent(DEPOSIT_EUR, bankBalance, ledgerBalance, false));
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
  void reconcile_shouldIgnoreLedgerEntriesAfterBankStatementDate() {
    LocalDate balanceDate = LocalDate.of(2024, 1, 15);
    BigDecimal bankBalance = new BigDecimal("1000.00");

    BankStatementBalance closingBalance = new BankStatementBalance(CLOSE, balanceDate, bankBalance);
    BankStatementAccount account =
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678");
    BankStatement bankStatement =
        new BankStatement(HISTORIC_STATEMENT, account, List.of(closingBalance), List.of());

    LedgerAccount ledgerAccount =
        systemAccountWithEntries(
            List.of(
                new EntryFixture(new BigDecimal("1000.00"), Instant.parse("2024-01-15T20:00:00Z")),
                new EntryFixture(new BigDecimal("50.00"), Instant.parse("2024-01-15T22:30:00Z"))));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount(), TKF100))
        .thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE123456789012345678")).thenReturn(DEPOSIT_EUR);

    // Should succeed: balance at Jan 15 = 1000.00, ignoring the +50 on Jan 16
    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
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
        systemAccountWithBalance(matchingBalance, Instant.parse("2024-01-15T12:00:00Z"));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount(), TKF100))
        .thenReturn(ledgerAccount);
    when(sebAccountConfiguration.getAccountType("EE123456789012345678")).thenReturn(DEPOSIT_EUR);

    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }
}
