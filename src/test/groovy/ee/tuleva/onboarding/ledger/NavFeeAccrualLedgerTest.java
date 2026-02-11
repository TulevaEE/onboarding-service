package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.NAV_EQUITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NavFeeAccrualLedgerTest {

  @Mock private LedgerAccountService ledgerAccountService;
  @Mock private LedgerTransactionService ledgerTransactionService;

  @Mock private LedgerAccount managementFeeAccount;
  @Mock private LedgerAccount depotFeeAccount;
  @Mock private LedgerAccount navEquityAccount;
  @Mock private LedgerTransaction transaction;

  @Captor private ArgumentCaptor<LedgerEntryDto[]> entriesCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> metadataCaptor;

  private NavFeeAccrualLedger navFeeAccrualLedger;

  private final Clock clock = Clock.fixed(Instant.parse("2026-02-01T12:00:00Z"), ZoneId.of("UTC"));

  @BeforeEach
  void setUp() {
    navFeeAccrualLedger =
        new NavFeeAccrualLedger(ledgerAccountService, ledgerTransactionService, clock);
  }

  private void setupAccountMocks() {
    when(ledgerAccountService.findSystemAccount(MANAGEMENT_FEE_ACCRUAL))
        .thenReturn(Optional.of(managementFeeAccount));
    when(ledgerAccountService.findSystemAccount(DEPOT_FEE_ACCRUAL))
        .thenReturn(Optional.of(depotFeeAccount));
    when(ledgerAccountService.findSystemAccount(NAV_EQUITY))
        .thenReturn(Optional.of(navEquityAccount));
  }

  @Test
  void recordFeeAccrual_recordsManagementFeeToLedger() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        "TKF100", accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"));

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(navEquityAccount);
    assertThat(entries[0].amount()).isEqualByComparingTo("52.05");
    assertThat(entries[1].account()).isEqualTo(managementFeeAccount);
    assertThat(entries[1].amount()).isEqualByComparingTo("-52.05");
  }

  @Test
  void recordFeeAccrual_recordsDepotFeeToLedger() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        "TKF100", accrualDate, DEPOT_FEE_ACCRUAL, new BigDecimal("16.44"));

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(navEquityAccount);
    assertThat(entries[0].amount()).isEqualByComparingTo("16.44");
    assertThat(entries[1].account()).isEqualTo(depotFeeAccount);
    assertThat(entries[1].amount()).isEqualByComparingTo("-16.44");
  }

  @Test
  void recordFeeAccrual_createsBalancedTransaction() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        "TKF100", accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"));

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    BigDecimal total =
        java.util.Arrays.stream(entries).map(LedgerEntryDto::amount).reduce(ZERO, BigDecimal::add);
    assertThat(total).isEqualByComparingTo(ZERO);
  }

  @Test
  void recordFeeAccrual_skipsWhenAmountIsNull() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);

    navFeeAccrualLedger.recordFeeAccrual("TKF100", accrualDate, MANAGEMENT_FEE_ACCRUAL, null);

    verify(ledgerTransactionService, never())
        .createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class));
  }

  @Test
  void recordFeeAccrual_skipsWhenAmountIsZero() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);

    navFeeAccrualLedger.recordFeeAccrual("TKF100", accrualDate, MANAGEMENT_FEE_ACCRUAL, ZERO);

    verify(ledgerTransactionService, never())
        .createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class));
  }

  @Test
  void recordFeeAccrual_includesMetadataWithFundFeeTypeAndDate() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        "TKF100", accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"));

    verify(ledgerTransactionService)
        .createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            metadataCaptor.capture(),
            any(LedgerEntryDto[].class));

    Map<String, Object> metadata = metadataCaptor.getValue();
    assertThat(metadata).containsEntry("operationType", "FEE_ACCRUAL");
    assertThat(metadata).containsEntry("fund", "TKF100");
    assertThat(metadata).containsEntry("feeType", "MANAGEMENT_FEE_ACCRUAL");
    assertThat(metadata).containsEntry("accrualDate", "2026-02-01");
  }
}
