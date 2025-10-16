package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.systemAccountWithBalance;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.swedbank.statement.BankStatementAccount;
import ee.tuleva.onboarding.swedbank.statement.BankStatementBalance;
import ee.tuleva.onboarding.swedbank.statement.BankStatementEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliatorTest {

  @Mock private LedgerService ledgerService;
  @Mock private SavingFundPaymentRepository paymentRepository;
  @Mock private SavingsFundLedger savingsFundLedger;
  @Mock private SwedbankAccountConfiguration swedbankAccountConfiguration;

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
    when(paymentRepository.findPaymentsWithStatus(any())).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(new ArrayList<>());
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE987654321098765432");
    when(swedbankAccountConfiguration.getAccountType("EE123456789012345678"))
        .thenReturn(DEPOSIT_EUR);

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

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(ledgerBalance, toEstonianTime(balanceDate.minusDays(1)));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);
    when(paymentRepository.findPaymentsWithStatus(any())).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(new ArrayList<>());
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE987654321098765432");
    when(swedbankAccountConfiguration.getAccountType("EE987700771001802057"))
        .thenReturn(DEPOSIT_EUR);

    // When & Then
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

    LedgerAccount ledgerAccount =
        systemAccountWithBalance(matchingBalance, toEstonianTime(balanceDate.minusDays(1)));

    when(ledgerService.getSystemAccount(DEPOSIT_EUR.getLedgerAccount())).thenReturn(ledgerAccount);
    when(paymentRepository.findPaymentsWithStatus(any())).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(new ArrayList<>());
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE987654321098765432");
    when(swedbankAccountConfiguration.getAccountType("EE123456789012345678"))
        .thenReturn(DEPOSIT_EUR);

    // When & Then
    assertDoesNotThrow(() -> reconciliator.reconcile(bankStatement));
  }

  @Test
  void detectAndFixMissingLedgerEntries_shouldRecordUnattributedPayments() {
    // Given
    UUID paymentId1 = UUID.randomUUID();
    UUID paymentId2 = UUID.randomUUID();

    SavingFundPayment toBeReturnedPayment =
        aPayment()
            .id(paymentId1)
            .status(TO_BE_RETURNED)
            .amount(new BigDecimal("100.00"))
            .remitterIban("EE123456789012345678")
            .beneficiaryIban("EE111111111111111111")
            .build();

    SavingFundPayment returnedPayment =
        aPayment()
            .id(paymentId2)
            .status(RETURNED)
            .amount(new BigDecimal("200.00"))
            .remitterIban("EE987654321098765432")
            .beneficiaryIban("EE222222222222222222")
            .build();

    when(paymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .thenReturn(List.of(toBeReturnedPayment));
    when(paymentRepository.findPaymentsWithStatus(RETURNED)).thenReturn(List.of(returnedPayment));
    when(paymentRepository.findAll()).thenReturn(new ArrayList<>());
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE987654321098765432");

    when(savingsFundLedger.hasLedgerEntry(paymentId1)).thenReturn(false);
    when(savingsFundLedger.hasLedgerEntry(paymentId2)).thenReturn(false);

    // When
    reconciliator.detectAndFixMissingLedgerEntries();

    // Then
    verify(savingsFundLedger).recordUnattributedPayment(new BigDecimal("100.00"), paymentId1);
    verify(savingsFundLedger).recordUnattributedPayment(new BigDecimal("200.00"), paymentId2);
  }

  @Test
  void detectAndFixMissingLedgerEntries_shouldRecordBounceBacksForReturns() {
    // Given
    UUID paymentId = UUID.randomUUID();
    String beneficiaryIban = "EE123456789012345678";
    String investmentIban = "EE987654321098765432";

    // Outgoing payment (negative amount) not to investment account
    SavingFundPayment returnPayment =
        aPayment()
            .id(paymentId)
            .amount(new BigDecimal("-150.00"))
            .remitterIban("EE555555555555555555")
            .beneficiaryIban(beneficiaryIban)
            .build();

    when(paymentRepository.findPaymentsWithStatus(any())).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(List.of(returnPayment));
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn(investmentIban);

    // Mock that bounce back entry doesn't exist yet
    when(savingsFundLedger.hasLedgerEntry(paymentId)).thenReturn(false);

    // When
    reconciliator.detectAndFixMissingLedgerEntries();

    // Then
    verify(savingsFundLedger).bounceBackUnattributedPayment(new BigDecimal("150.00"), paymentId);
  }

  @Test
  void detectAndFixMissingLedgerEntries_shouldNotRecordBounceBacksForInvestmentTransfers() {
    // Given
    UUID paymentId = UUID.randomUUID();
    String investmentIban = "EE987654321098765432";

    // Outgoing payment to investment account (should not be bounced back)
    SavingFundPayment investmentTransfer =
        aPayment()
            .id(paymentId)
            .amount(new BigDecimal("-1000.00"))
            .remitterIban("EE666666666666666666")
            .beneficiaryIban(investmentIban)
            .build();

    when(paymentRepository.findPaymentsWithStatus(any())).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(List.of(investmentTransfer));
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn(investmentIban);

    // When
    reconciliator.detectAndFixMissingLedgerEntries();

    // Then
    verify(savingsFundLedger, never()).bounceBackUnattributedPayment(any(), any());
  }

  @Test
  void detectAndFixMissingLedgerEntries_shouldNotRecordDuplicates() {
    // Given
    UUID paymentId = UUID.randomUUID();

    SavingFundPayment payment =
        aPayment()
            .id(paymentId)
            .status(TO_BE_RETURNED)
            .amount(new BigDecimal("100.00"))
            .remitterIban("EE123456789012345678")
            .build();

    when(paymentRepository.findPaymentsWithStatus(TO_BE_RETURNED)).thenReturn(List.of(payment));
    when(paymentRepository.findPaymentsWithStatus(RETURNED)).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(new ArrayList<>());
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE987654321098765432");

    when(savingsFundLedger.hasLedgerEntry(paymentId)).thenReturn(false, true);

    // When - Call twice to test duplicate prevention
    reconciliator.detectAndFixMissingLedgerEntries();
    reconciliator.detectAndFixMissingLedgerEntries();

    // Then - Should only be recorded once
    verify(savingsFundLedger, times(1))
        .recordUnattributedPayment(new BigDecimal("100.00"), paymentId);
  }

  @Test
  void detectAndFixMissingLedgerEntries_shouldHandleExceptions() {
    // Given
    UUID paymentId1 = UUID.randomUUID();
    UUID paymentId2 = UUID.randomUUID();

    SavingFundPayment payment1 =
        aPayment()
            .id(paymentId1)
            .status(TO_BE_RETURNED)
            .amount(new BigDecimal("100.00"))
            .remitterIban("EE123456789012345678")
            .build();

    SavingFundPayment payment2 =
        aPayment()
            .id(paymentId2)
            .status(TO_BE_RETURNED)
            .amount(new BigDecimal("200.00"))
            .remitterIban("EE987654321098765432")
            .build();

    when(paymentRepository.findPaymentsWithStatus(TO_BE_RETURNED))
        .thenReturn(List.of(payment1, payment2));
    when(paymentRepository.findPaymentsWithStatus(RETURNED)).thenReturn(new ArrayList<>());
    when(paymentRepository.findAll()).thenReturn(new ArrayList<>());
    when(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
        .thenReturn("EE987654321098765432");

    when(savingsFundLedger.hasLedgerEntry(paymentId1)).thenReturn(false);
    when(savingsFundLedger.hasLedgerEntry(paymentId2)).thenReturn(false);

    // First call throws exception, second succeeds
    doThrow(new RuntimeException("Test exception"))
        .when(savingsFundLedger)
        .recordUnattributedPayment(any(), eq(paymentId1));

    // When
    reconciliator.detectAndFixMissingLedgerEntries();

    // Then - Second payment should still be processed
    verify(savingsFundLedger).recordUnattributedPayment(new BigDecimal("200.00"), paymentId2);
  }

  private static Instant toEstonianTime(LocalDate date) {
    return date.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
  }
}
