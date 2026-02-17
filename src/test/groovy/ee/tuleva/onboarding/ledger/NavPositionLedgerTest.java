package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
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
class NavPositionLedgerTest {

  @Mock private LedgerAccountService ledgerAccountService;
  @Mock private LedgerTransactionService ledgerTransactionService;

  @Mock private LedgerAccount securitiesUnitsAccount1;
  @Mock private LedgerAccount securitiesUnitsEquityAccount1;
  @Mock private LedgerAccount securitiesUnitsAccount2;
  @Mock private LedgerAccount securitiesUnitsEquityAccount2;
  @Mock private LedgerAccount cashAccount;
  @Mock private LedgerAccount receivablesAccount;
  @Mock private LedgerAccount payablesAccount;
  @Mock private LedgerAccount navEquityAccount;
  @Mock private LedgerTransaction transaction;

  @Captor private ArgumentCaptor<LedgerEntryDto[]> entriesCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> metadataCaptor;

  private NavPositionLedger navPositionLedger;

  private final Clock clock = Clock.fixed(Instant.parse("2026-02-01T12:00:00Z"), ZoneId.of("UTC"));

  @BeforeEach
  void setUp() {
    navPositionLedger =
        new NavPositionLedger(ledgerAccountService, ledgerTransactionService, clock);
  }

  private void setupAccountMocks() {
    when(ledgerAccountService.findSystemAccountByName(
            SECURITIES_UNITS.getAccountName("IE00BFG1TM61"), ASSET, FUND_UNIT))
        .thenReturn(Optional.of(securitiesUnitsAccount1));
    when(ledgerAccountService.findSystemAccountByName(
            SECURITIES_UNITS_EQUITY.getAccountName("IE00BFG1TM61"), LIABILITY, FUND_UNIT))
        .thenReturn(Optional.of(securitiesUnitsEquityAccount1));
    when(ledgerAccountService.findSystemAccountByName(
            SECURITIES_UNITS.getAccountName("IE00BMDBMY19"), ASSET, FUND_UNIT))
        .thenReturn(Optional.of(securitiesUnitsAccount2));
    when(ledgerAccountService.findSystemAccountByName(
            SECURITIES_UNITS_EQUITY.getAccountName("IE00BMDBMY19"), LIABILITY, FUND_UNIT))
        .thenReturn(Optional.of(securitiesUnitsEquityAccount2));
    when(ledgerAccountService.findSystemAccount(CASH_POSITION))
        .thenReturn(Optional.of(cashAccount));
    when(ledgerAccountService.findSystemAccount(TRADE_RECEIVABLES))
        .thenReturn(Optional.of(receivablesAccount));
    when(ledgerAccountService.findSystemAccount(TRADE_PAYABLES))
        .thenReturn(Optional.of(payablesAccount));
    when(ledgerAccountService.findSystemAccount(NAV_EQUITY))
        .thenReturn(Optional.of(navEquityAccount));
  }

  @Test
  void recordPositions_recordsPerIsinSecuritiesUnitsToLedger() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navPositionLedger.recordPositions(
        "TKF100",
        reportDate,
        Map.of("IE00BFG1TM61", new BigDecimal("1000.00000")),
        ZERO,
        ZERO,
        ZERO);

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(securitiesUnitsAccount1);
    assertThat(entries[0].amount()).isEqualByComparingTo("1000.00000");
    assertThat(entries[1].account()).isEqualTo(securitiesUnitsEquityAccount1);
    assertThat(entries[1].amount()).isEqualByComparingTo("-1000.00000");
  }

  @Test
  void recordPositions_recordsCashPositionToLedger() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navPositionLedger.recordPositions(
        "TKF100", reportDate, Map.of(), new BigDecimal("50000.00"), ZERO, ZERO);

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(cashAccount);
    assertThat(entries[0].amount()).isEqualByComparingTo("50000.00");
    assertThat(entries[1].account()).isEqualTo(navEquityAccount);
    assertThat(entries[1].amount()).isEqualByComparingTo("-50000.00");
  }

  @Test
  void recordPositions_recordsReceivablesToLedger() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navPositionLedger.recordPositions(
        "TKF100", reportDate, Map.of(), ZERO, new BigDecimal("10000.00"), ZERO);

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(receivablesAccount);
    assertThat(entries[0].amount()).isEqualByComparingTo("10000.00");
    assertThat(entries[1].account()).isEqualTo(navEquityAccount);
    assertThat(entries[1].amount()).isEqualByComparingTo("-10000.00");
  }

  @Test
  void recordPositions_recordsPayablesToLedger() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navPositionLedger.recordPositions(
        "TKF100", reportDate, Map.of(), ZERO, ZERO, new BigDecimal("-5000.00"));

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(2);
    assertThat(entries[0].account()).isEqualTo(payablesAccount);
    assertThat(entries[0].amount()).isEqualByComparingTo("-5000.00");
    assertThat(entries[1].account()).isEqualTo(navEquityAccount);
    assertThat(entries[1].amount()).isEqualByComparingTo("5000.00");
  }

  @Test
  void recordPositions_recordsAllPositionTypesInSingleTransaction() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navPositionLedger.recordPositions(
        "TKF100",
        reportDate,
        Map.of(
            "IE00BFG1TM61", new BigDecimal("1000.00000"),
            "IE00BMDBMY19", new BigDecimal("500.00000")),
        new BigDecimal("50000.00"),
        new BigDecimal("10000.00"),
        new BigDecimal("-5000.00"));

    verify(ledgerTransactionService)
        .createTransaction(eq(TRANSFER), eq(Instant.now(clock)), any(), entriesCaptor.capture());

    LedgerEntryDto[] entries = entriesCaptor.getValue();
    assertThat(entries).hasSize(10);
  }

  @Test
  void recordPositions_skipsWhenAllPositionsAreZero() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);

    navPositionLedger.recordPositions("TKF100", reportDate, Map.of(), ZERO, ZERO, ZERO);

    verify(ledgerTransactionService, never())
        .createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class));
  }

  @Test
  void recordPositions_includesMetadataWithFundAndReportDate() {
    LocalDate reportDate = LocalDate.of(2026, 2, 1);
    setupAccountMocks();

    when(ledgerTransactionService.createTransaction(
            any(TransactionType.class), any(Instant.class), any(), any(LedgerEntryDto[].class)))
        .thenReturn(transaction);

    navPositionLedger.recordPositions(
        "TKF100",
        reportDate,
        Map.of("IE00BFG1TM61", new BigDecimal("1000.00000")),
        ZERO,
        ZERO,
        ZERO);

    verify(ledgerTransactionService)
        .createTransaction(
            any(TransactionType.class),
            any(Instant.class),
            metadataCaptor.capture(),
            any(LedgerEntryDto[].class));

    Map<String, Object> metadata = metadataCaptor.getValue();
    assertThat(metadata).containsEntry("operationType", "POSITION_UPDATE");
    assertThat(metadata).containsEntry("fund", "TKF100");
    assertThat(metadata).containsEntry("reportDate", "2026-02-01");
  }
}
