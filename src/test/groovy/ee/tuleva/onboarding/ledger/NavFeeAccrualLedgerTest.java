package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Mock private LedgerAccountService ledgerAccountService;
  @Mock private LedgerTransactionService ledgerTransactionService;
  @Mock private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

  @Mock private LedgerAccount managementFeeAccount;
  @Mock private LedgerAccount depotFeeAccount;
  @Mock private LedgerAccount navEquityAccount;
  @Mock private LedgerAccount blackrockAdjustmentAccount;
  @Mock private LedgerTransaction transaction;

  @Captor private ArgumentCaptor<LedgerEntryDto[]> entriesCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> metadataCaptor;

  private NavFeeAccrualLedger navFeeAccrualLedger;

  @BeforeEach
  void setUp() {
    navFeeAccrualLedger =
        new NavFeeAccrualLedger(ledgerAccountService, ledgerTransactionService, jdbcClient);
  }

  private void setupAccountMocks() {
    when(ledgerAccountService.findSystemAccount(MANAGEMENT_FEE_ACCRUAL, TKF100))
        .thenReturn(Optional.of(managementFeeAccount));
    when(ledgerAccountService.findSystemAccount(DEPOT_FEE_ACCRUAL, TKF100))
        .thenReturn(Optional.of(depotFeeAccount));
    when(ledgerAccountService.findSystemAccount(NAV_EQUITY, TKF100))
        .thenReturn(Optional.of(navEquityAccount));
  }

  @Test
  void recordFeeAccrual_recordsManagementFeeToLedger() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    Instant expectedTimestamp = accrualDate.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant();
    setupAccountMocks();

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(), eq(FEE_ACCRUAL)))
        .thenReturn(false);
    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"), Map.of());

    verify(ledgerTransactionService)
        .createTransaction(
            eq(FEE_ACCRUAL),
            eq(expectedTimestamp),
            any(UUID.class),
            any(),
            entriesCaptor.capture());

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
    Instant expectedTimestamp = accrualDate.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant();
    setupAccountMocks();

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(), eq(FEE_ACCRUAL)))
        .thenReturn(false);
    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, accrualDate, DEPOT_FEE_ACCRUAL, new BigDecimal("16.44"), Map.of());

    verify(ledgerTransactionService)
        .createTransaction(
            eq(FEE_ACCRUAL),
            eq(expectedTimestamp),
            any(UUID.class),
            any(),
            entriesCaptor.capture());

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

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(), eq(FEE_ACCRUAL)))
        .thenReturn(false);
    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"), Map.of());

    verify(ledgerTransactionService)
        .createTransaction(
            eq(FEE_ACCRUAL), any(Instant.class), any(UUID.class), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    BigDecimal total =
        java.util.Arrays.stream(entries).map(LedgerEntryDto::amount).reduce(ZERO, BigDecimal::add);
    assertThat(total).isEqualByComparingTo(ZERO);
  }

  @Test
  void recordFeeAccrual_skipsWhenAmountIsNull() {
    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, LocalDate.of(2026, 2, 1), MANAGEMENT_FEE_ACCRUAL, null, Map.of());

    verifyNoInteractions(ledgerTransactionService);
  }

  @Test
  void recordFeeAccrual_skipsWhenAmountIsZero() {
    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, LocalDate.of(2026, 2, 1), MANAGEMENT_FEE_ACCRUAL, ZERO, Map.of());

    verifyNoInteractions(ledgerTransactionService);
  }

  @Test
  void recordFeeAccrual_forwardsMetadataToTransaction() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            any(), eq(FEE_ACCRUAL)))
        .thenReturn(false);
    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    Map<String, Object> metadata =
        Map.of(
            "operationType", "FEE_ACCRUAL", "fund", "TKF100", "feeType", "MANAGEMENT_FEE_ACCRUAL");
    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"), metadata);

    verify(ledgerTransactionService)
        .createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            metadataCaptor.capture(),
            any(LedgerEntryDto[].class));

    assertThat(metadataCaptor.getValue()).isSameAs(metadata);
  }

  @Test
  void recordFeeAccrual_skipsWhenEntryAlreadyExists() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    UUID expectedRef =
        UUID.nameUUIDFromBytes("TKF100:2026-02-01:MANAGEMENT_FEE_ACCRUAL".getBytes());

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            expectedRef, FEE_ACCRUAL))
        .thenReturn(true);

    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"), Map.of());

    verify(ledgerTransactionService, never())
        .createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class));
  }

  @Test
  void recordFeeAccrual_generatesDeterministicReference() {
    LocalDate accrualDate = LocalDate.of(2026, 2, 1);
    Instant expectedTimestamp = accrualDate.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant();
    setupAccountMocks();

    UUID expectedRef =
        UUID.nameUUIDFromBytes("TKF100:2026-02-01:MANAGEMENT_FEE_ACCRUAL".getBytes());

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            expectedRef, FEE_ACCRUAL))
        .thenReturn(false);
    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.recordFeeAccrual(
        TKF100, accrualDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("52.05"), Map.of());

    verify(ledgerTransactionService)
        .createTransaction(
            eq(FEE_ACCRUAL),
            eq(expectedTimestamp),
            eq(expectedRef),
            any(),
            any(LedgerEntryDto[].class));
  }

  @Test
  void recordBlackrockAdjustment_recordsAdjustmentWhenDeltaIsNonZero() {
    LocalDate date = LocalDate.of(2026, 4, 2);
    given(ledgerAccountService.findSystemAccount(BLACKROCK_ADJUSTMENT, TUK75))
        .willReturn(Optional.of(blackrockAdjustmentAccount));
    given(ledgerAccountService.findSystemAccount(NAV_EQUITY, TUK75))
        .willReturn(Optional.of(navEquityAccount));
    given(blackrockAdjustmentAccount.getBalanceAt(any())).willReturn(ZERO);
    given(
            ledgerTransactionService.createTransaction(
                any(LedgerTransaction.TransactionType.class),
                any(Instant.class),
                any(UUID.class),
                any(),
                any(LedgerEntryDto[].class)))
        .willReturn(transaction);

    var result =
        navFeeAccrualLedger.recordBlackrockAdjustment(TUK75, date, new BigDecimal("38531.70"));

    assertThat(result.previousBalance()).isEqualByComparingTo(ZERO);
    assertThat(result.targetBalance()).isEqualByComparingTo("38531.70");
    assertThat(result.delta()).isEqualByComparingTo("38531.70");
    assertThat(result.transactionCreated()).isTrue();
  }

  @Test
  void recordBlackrockAdjustment_skipsWhenDeltaIsZero() {
    LocalDate date = LocalDate.of(2026, 4, 2);
    given(ledgerAccountService.findSystemAccount(BLACKROCK_ADJUSTMENT, TUK75))
        .willReturn(Optional.of(blackrockAdjustmentAccount));
    given(blackrockAdjustmentAccount.getBalanceAt(any())).willReturn(new BigDecimal("38531.70"));

    var result =
        navFeeAccrualLedger.recordBlackrockAdjustment(TUK75, date, new BigDecimal("38531.70"));

    assertThat(result.delta()).isEqualByComparingTo(ZERO);
    assertThat(result.transactionCreated()).isFalse();
    then(ledgerTransactionService).shouldHaveNoInteractions();
  }

  @Test
  void recordBlackrockAdjustment_computesDeltaFromExistingBalance() {
    LocalDate date = LocalDate.of(2026, 4, 2);
    given(ledgerAccountService.findSystemAccount(BLACKROCK_ADJUSTMENT, TUK75))
        .willReturn(Optional.of(blackrockAdjustmentAccount));
    given(ledgerAccountService.findSystemAccount(NAV_EQUITY, TUK75))
        .willReturn(Optional.of(navEquityAccount));
    given(blackrockAdjustmentAccount.getBalanceAt(any())).willReturn(new BigDecimal("30000.00"));
    given(
            ledgerTransactionService.createTransaction(
                any(LedgerTransaction.TransactionType.class),
                any(Instant.class),
                any(UUID.class),
                any(),
                any(LedgerEntryDto[].class)))
        .willReturn(transaction);

    var result =
        navFeeAccrualLedger.recordBlackrockAdjustment(TUK75, date, new BigDecimal("38531.70"));

    assertThat(result.previousBalance()).isEqualByComparingTo("30000.00");
    assertThat(result.delta()).isEqualByComparingTo("8531.70");
    assertThat(result.transactionCreated()).isTrue();
  }

  @Test
  void recordBlackrockAdjustment_handlesNegativeTargetForFees() {
    LocalDate date = LocalDate.of(2026, 4, 2);
    given(ledgerAccountService.findSystemAccount(BLACKROCK_ADJUSTMENT, TUK00))
        .willReturn(Optional.of(blackrockAdjustmentAccount));
    given(ledgerAccountService.findSystemAccount(NAV_EQUITY, TUK00))
        .willReturn(Optional.of(navEquityAccount));
    given(blackrockAdjustmentAccount.getBalanceAt(any())).willReturn(ZERO);
    given(
            ledgerTransactionService.createTransaction(
                any(LedgerTransaction.TransactionType.class),
                any(Instant.class),
                any(UUID.class),
                any(),
                any(LedgerEntryDto[].class)))
        .willReturn(transaction);

    var result =
        navFeeAccrualLedger.recordBlackrockAdjustment(TUK00, date, new BigDecimal("-2295.25"));

    assertThat(result.delta()).isEqualByComparingTo("-2295.25");
    assertThat(result.transactionCreated()).isTrue();
  }

  @Test
  void settleFeeAccrual_reducesLiabilityAndCreditsNavEquity() {
    LocalDate settlementDate = LocalDate.of(2026, 2, 28);
    Instant expectedTimestamp = settlementDate.atTime(12, 0).atZone(ESTONIAN_ZONE).toInstant();
    setupAccountMocks();

    UUID expectedRef =
        UUID.nameUUIDFromBytes("TKF100:2026-02-28:MANAGEMENT_FEE_ACCRUAL:SETTLEMENT".getBytes());

    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(
            expectedRef, FEE_SETTLEMENT))
        .thenReturn(false);
    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            any(UUID.class),
            any(),
            any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navFeeAccrualLedger.settleFeeAccrual(
        TKF100, settlementDate, MANAGEMENT_FEE_ACCRUAL, new BigDecimal("1500.00"));

    verify(ledgerTransactionService)
        .createTransaction(
            eq(FEE_SETTLEMENT),
            eq(expectedTimestamp),
            eq(expectedRef),
            any(),
            entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(managementFeeAccount);
    assertThat(entries[0].amount()).isEqualByComparingTo("1500.00");
    assertThat(entries[1].account()).isEqualTo(navEquityAccount);
    assertThat(entries[1].amount()).isEqualByComparingTo("-1500.00");
  }
}
