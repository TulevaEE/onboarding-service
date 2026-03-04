package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_COUNT_UPDATE;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_EQUITY;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.math.BigDecimal.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EpisUnitCountLedgerRecorderTest {

  @Mock private LedgerAccountService ledgerAccountService;
  @Mock private LedgerTransactionService ledgerTransactionService;

  @InjectMocks private EpisUnitCountLedgerRecorder recorder;

  private static final LocalDate DATE = LocalDate.of(2025, 3, 14);

  @Test
  void recordUnitCount_recordsTotalUnits() {
    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(UUID.class), eq(UNIT_COUNT_UPDATE)))
        .thenReturn(false);

    LedgerAccount unitsAccount = mockAccountWithBalance(FUND_UNITS_OUTSTANDING, ZERO);
    LedgerAccount equityAccount = mockAccount(FUND_UNITS_EQUITY);

    recorder.recordUnitCount(TUK75, DATE, new BigDecimal("1050000.00000"));

    verify(ledgerTransactionService)
        .createTransaction(
            eq(UNIT_COUNT_UPDATE),
            any(),
            any(UUID.class),
            any(),
            eq(new LedgerEntryDto(unitsAccount, new BigDecimal("1050000.00000"))),
            eq(new LedgerEntryDto(equityAccount, new BigDecimal("-1050000.00000"))));
  }

  @Test
  void recordUnitCount_recordsDeltaFromCurrentBalance() {
    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(UUID.class), eq(UNIT_COUNT_UPDATE)))
        .thenReturn(false);

    LedgerAccount unitsAccount =
        mockAccountWithBalance(FUND_UNITS_OUTSTANDING, new BigDecimal("1000000.00000"));
    LedgerAccount equityAccount = mockAccount(FUND_UNITS_EQUITY);

    recorder.recordUnitCount(TUK75, DATE, new BigDecimal("1150000.00000"));

    verify(ledgerTransactionService)
        .createTransaction(
            eq(UNIT_COUNT_UPDATE),
            any(),
            any(UUID.class),
            any(),
            eq(new LedgerEntryDto(unitsAccount, new BigDecimal("150000.00000"))),
            eq(new LedgerEntryDto(equityAccount, new BigDecimal("-150000.00000"))));
  }

  @Test
  void recordUnitCount_isIdempotent() {
    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(UUID.class), eq(UNIT_COUNT_UPDATE)))
        .thenReturn(true);

    recorder.recordUnitCount(TUK75, DATE, new BigDecimal("1050000.00000"));

    verify(ledgerTransactionService, never()).createTransaction(any(), any(), any(), any());
  }

  @Test
  void recordUnitCount_skipsWhenDeltaIsZero() {
    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(UUID.class), eq(UNIT_COUNT_UPDATE)))
        .thenReturn(false);

    mockAccountWithBalance(FUND_UNITS_OUTSTANDING, new BigDecimal("1050000.00000"));

    recorder.recordUnitCount(TUK75, DATE, new BigDecimal("1050000.00000"));

    verify(ledgerTransactionService, never()).createTransaction(any(), any(), any(), any());
  }

  private LedgerAccount mockAccountWithBalance(SystemAccount systemAccount, BigDecimal balance) {
    LedgerAccount account = mock(LedgerAccount.class);
    when(account.getBalance()).thenReturn(balance);
    when(ledgerAccountService.findSystemAccount(systemAccount, TUK75))
        .thenReturn(Optional.of(account));
    return account;
  }

  private LedgerAccount mockAccount(SystemAccount systemAccount) {
    LedgerAccount account = mock(LedgerAccount.class);
    when(ledgerAccountService.findSystemAccount(systemAccount, TUK75))
        .thenReturn(Optional.of(account));
    return account;
  }
}
