package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

  private static final LocalDate ACCRUAL_DAY = LocalDate.of(2000, 1, 2);

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Mock private FeeCalculator calculator1;
  @Mock private FeeCalculator calculator2;
  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavFeeAccrualLedger navFeeAccrualLedger;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private FeeChargedToFundPolicy feeChargedToFundPolicy;

  private FeeCalculationService service;

  private final FeeMonthResolver feeMonthResolver = new FeeMonthResolver();

  @BeforeEach
  void setUp() {
    service =
        new FeeCalculationService(
            List.of(calculator1, calculator2),
            feeAccrualRepository,
            navFeeAccrualLedger,
            navLedgerRepository,
            feeMonthResolver,
            feeChargedToFundPolicy,
            new PublicHolidays());
    lenient()
        .when(feeChargedToFundPolicy.resolverFor(any(), any()))
        .thenAnswer(call -> alwaysCharged(call.getArgument(0), call.getArgument(1), true));
  }

  private FeeChargedToFundPolicy.Resolver alwaysCharged(
      TulevaFund fund, FeeType feeType, boolean chargedToFund) {
    return new FeeChargedToFundPolicy.Resolver(
        fund,
        feeType,
        List.of(
            new FeeChargedToFundPolicy.Policy(
                chargedToFund, LocalDate.of(2017, 3, 28), (LocalDate) null)));
  }

  @Test
  void calculateFeesForNav_recordsTheAccrualButKeepsTheFundLedgerCleanWhenTulevaBearsTheFee() {
    LocalDate positionReportDate = LocalDate.of(2025, 1, 13);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual mgmtAccrual = createAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate);
    FeeAccrual depotAccrual = createAccrual(TKF100, FeeType.DEPOT, positionReportDate);

    when(feeChargedToFundPolicy.resolverFor(TKF100, FeeType.DEPOT))
        .thenReturn(alwaysCharged(TKF100, FeeType.DEPOT, false));
    when(feeAccrualRepository.findLatestAccrualDate(TKF100))
        .thenReturn(Optional.of(positionReportDate.minusDays(1)));
    when(feeAccrualRepository.findLatestBaseValue(eq(TKF100), any(FeeType.class)))
        .thenReturn(Optional.of(baseValue));
    when(calculator1.calculate(eq(TKF100), any(LocalDate.class), any(FeeBases.class)))
        .thenReturn(mgmtAccrual);
    when(calculator2.calculate(eq(TKF100), any(LocalDate.class), any(FeeBases.class)))
        .thenReturn(depotAccrual);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(
        TKF100, positionReportDate, new FeeBases(baseValue, baseValue), feeCutoff, null);

    verify(feeAccrualRepository).save(depotAccrual);
    verify(navFeeAccrualLedger)
        .recordFeeAccrual(
            eq(TKF100), eq(positionReportDate), eq(MANAGEMENT_FEE_ACCRUAL), any(), any());
    verify(navFeeAccrualLedger, never())
        .recordFeeAccrual(any(), any(), eq(DEPOT_FEE_ACCRUAL), any(), any());
  }

  @Test
  void calculateFeesForNav_calculatesAndRecordsForPendingDays() {
    // Jan 13 (Mon) is positionReportDate, last accrual was Jan 9 (Thu)
    // Catch-up days Jan 10-12 use previous base value, Jan 13 uses current
    LocalDate positionReportDate = LocalDate.of(2025, 1, 13);
    BigDecimal baseValue = new BigDecimal("12000000");
    BigDecimal previousBaseValue = new BigDecimal("11500000");
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual mgmtAccrual = createAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate);
    FeeAccrual depotAccrual = createAccrual(TKF100, FeeType.DEPOT, positionReportDate);

    when(feeAccrualRepository.findLatestAccrualDate(TKF100))
        .thenReturn(Optional.of(LocalDate.of(2025, 1, 9)));
    when(feeAccrualRepository.findLatestBaseValue(eq(TKF100), any(FeeType.class)))
        .thenReturn(Optional.of(previousBaseValue));
    when(calculator1.calculate(eq(TKF100), any(LocalDate.class), any(FeeBases.class)))
        .thenReturn(mgmtAccrual);
    when(calculator2.calculate(eq(TKF100), any(LocalDate.class), any(FeeBases.class)))
        .thenReturn(depotAccrual);
    stubZeroLedgerBalance();
    when(feeAccrualRepository.getUnsettledAccrualByDate(
            TKF100, FeeType.MANAGEMENT, positionReportDate))
        .thenReturn(Map.of(ACCRUAL_DAY, new BigDecimal("400.00")));
    when(feeAccrualRepository.getUnsettledAccrualByDate(TKF100, FeeType.DEPOT, positionReportDate))
        .thenReturn(Map.of(ACCRUAL_DAY, new BigDecimal("50.00")));

    FeeResult result =
        service.calculateFeesForNav(
            TKF100, positionReportDate, new FeeBases(baseValue, baseValue), feeCutoff, null);

    // Catch-up days (Jan 10-12) use previous base value
    for (int day = 10; day <= 12; day++) {
      verify(calculator1)
          .calculate(
              eq(TKF100),
              eq(LocalDate.of(2025, 1, day)),
              eq(new FeeBases(previousBaseValue, previousBaseValue)));
      verify(calculator2)
          .calculate(
              eq(TKF100),
              eq(LocalDate.of(2025, 1, day)),
              eq(new FeeBases(previousBaseValue, previousBaseValue)));
    }
    // Position report date (Jan 13) uses current base value
    verify(calculator1)
        .calculate(eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue)));
    verify(calculator2)
        .calculate(eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue)));
    verify(feeAccrualRepository, times(8)).save(any(FeeAccrual.class));
    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("400.00");
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo("50.00");
  }

  @Test
  void calculateFeesForNav_defaultsToPositionReportDateWhenNoAccruals() {
    LocalDate positionReportDate = LocalDate.of(2025, 1, 13);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual accrual = createAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate);

    when(feeAccrualRepository.findLatestAccrualDate(TKF100)).thenReturn(Optional.empty());
    when(feeAccrualRepository.findLatestBaseValue(eq(TKF100), any(FeeType.class)))
        .thenReturn(Optional.empty());
    when(calculator1.calculate(
            eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual);
    when(calculator2.calculate(
            eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(
        TKF100, positionReportDate, new FeeBases(baseValue, baseValue), feeCutoff, null);

    verify(calculator1, times(1))
        .calculate(eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue)));
    verify(calculator2, times(1))
        .calculate(eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue)));
  }

  @Test
  void calculateFeesForNav_includesSecurityPricesInMetadata() {
    LocalDate positionReportDate = LocalDate.of(2025, 1, 13);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual accrual = createAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate);
    Map<String, ResolvedPrice> securityPrices =
        Map.of(
            "IE00BFG1TM61",
            ResolvedPrice.builder()
                .usedPrice(new BigDecimal("11.50"))
                .storageKey("IE00BFG1TM61.EUFUND")
                .priceDate(positionReportDate)
                .build());

    when(feeAccrualRepository.findLatestAccrualDate(TKF100)).thenReturn(Optional.empty());
    when(calculator1.calculate(
            eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual);
    when(calculator2.calculate(
            eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(
        TKF100, positionReportDate, new FeeBases(baseValue, baseValue), feeCutoff, securityPrices);

    verify(navFeeAccrualLedger, atLeastOnce())
        .recordFeeAccrual(
            eq(TKF100),
            eq(positionReportDate),
            any(),
            any(),
            argThat(metadata -> metadata.containsKey("securityPrices")));
  }

  @Test
  void calculateFeesForNav_settlesPreviousMonth() {
    LocalDate mar1 = LocalDate.of(2026, 3, 1);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff = mar1.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual accrual1 = createAccrual(TKF100, FeeType.MANAGEMENT, mar1);
    FeeAccrual accrual2 = createAccrual(TKF100, FeeType.DEPOT, mar1);

    when(feeAccrualRepository.findLatestAccrualDate(TKF100)).thenReturn(Optional.empty());
    when(calculator1.calculate(eq(TKF100), eq(mar1), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual1);
    when(calculator2.calculate(eq(TKF100), eq(mar1), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual2);

    Instant settlementCutoff =
        LocalDate.of(2026, 3, 1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), settlementCutoff))
        .thenReturn(new BigDecimal("-1500.00"));
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            DEPOT_FEE_ACCRUAL.getAccountName(TKF100), settlementCutoff))
        .thenReturn(new BigDecimal("-200.00"));
    when(feeAccrualRepository.getUnsettledAccrualByDate(TKF100, FeeType.MANAGEMENT, mar1))
        .thenReturn(Map.of());
    when(feeAccrualRepository.getUnsettledAccrualByDate(TKF100, FeeType.DEPOT, mar1))
        .thenReturn(Map.of());

    service.calculateFeesForNav(TKF100, mar1, new FeeBases(baseValue, baseValue), feeCutoff, null);

    verify(navFeeAccrualLedger)
        .settleFeeAccrual(
            TKF100, LocalDate.of(2026, 2, 28), MANAGEMENT_FEE_ACCRUAL, new BigDecimal("1500.00"));
    verify(navFeeAccrualLedger)
        .settleFeeAccrual(
            TKF100, LocalDate.of(2026, 2, 28), DEPOT_FEE_ACCRUAL, new BigDecimal("200.00"));
  }

  @Test
  void calculateFeesForNav_doesNotSettleMidMonth() {
    LocalDate feb15 = LocalDate.of(2026, 2, 15);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff = feb15.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual accrual1 = createAccrual(TKF100, FeeType.MANAGEMENT, feb15);
    FeeAccrual accrual2 = createAccrual(TKF100, FeeType.DEPOT, feb15);

    when(feeAccrualRepository.findLatestAccrualDate(TKF100)).thenReturn(Optional.empty());
    when(calculator1.calculate(eq(TKF100), eq(feb15), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual1);
    when(calculator2.calculate(eq(TKF100), eq(feb15), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual2);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(TKF100, feb15, new FeeBases(baseValue, baseValue), feeCutoff, null);

    verify(navFeeAccrualLedger, never()).settleFeeAccrual(any(), any(), any(), any());
  }

  private void stubZeroLedgerBalance() {
    when(navLedgerRepository.getSystemAccountBalanceBefore(any(), any(Instant.class)))
        .thenReturn(ZERO);
    when(feeAccrualRepository.getUnsettledAccrualByDate(any(), any(), any(LocalDate.class)))
        .thenReturn(Map.of());
  }

  @Test
  void calculateFeesForNav_returnsFeeFromAccrualRepository() {
    LocalDate positionReportDate = LocalDate.of(2025, 1, 13);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual mgmtAccrual = createAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate);
    FeeAccrual depotAccrual = createAccrual(TKF100, FeeType.DEPOT, positionReportDate);

    when(feeAccrualRepository.findLatestAccrualDate(TKF100)).thenReturn(Optional.empty());
    when(calculator1.calculate(
            eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(mgmtAccrual);
    when(calculator2.calculate(
            eq(TKF100), eq(positionReportDate), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(depotAccrual);
    stubZeroLedgerBalance();
    when(feeAccrualRepository.getUnsettledAccrualByDate(
            TKF100, FeeType.MANAGEMENT, positionReportDate))
        .thenReturn(Map.of(ACCRUAL_DAY, new BigDecimal("400.12")));
    when(feeAccrualRepository.getUnsettledAccrualByDate(TKF100, FeeType.DEPOT, positionReportDate))
        .thenReturn(Map.of(ACCRUAL_DAY, new BigDecimal("50.34")));

    FeeResult result =
        service.calculateFeesForNav(
            TKF100, positionReportDate, new FeeBases(baseValue, baseValue), feeCutoff, null);

    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("400.12");
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo("50.34");
  }

  private FeeAccrual createAccrual(TulevaFund fund, FeeType feeType, LocalDate date) {
    return createAccrual(fund, feeType, date, ZERO, new BigDecimal("10.000000"));
  }

  @Test
  void calculateFeesForNav_leavesAnAlreadyReconciledDayAlone() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    BigDecimal base = new BigDecimal("1142843733.04");
    FeeAccrual mgmtAccrual =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, base, new BigDecimal("6418.711377"));
    FeeAccrual depotAccrual = createAccrual(TUK75, FeeType.DEPOT, day, base, ZERO);
    givenCalculated(TUK75, day, base, mgmtAccrual, depotAccrual);
    givenStoredRows(TUK75, day, mgmtAccrual, depotAccrual);
    givenLedgerEntries(TUK75, MANAGEMENT_FEE_ACCRUAL, day, "-6418.71");
    givenLedgerEntries(TUK75, DEPOT_FEE_ACCRUAL, day);

    service.calculateFeesForNav(TUK75, day, new FeeBases(base, base), cutoffAfter(day), null);

    verify(feeAccrualRepository, never()).save(any());
    verify(navFeeAccrualLedger, never()).recordFeeAccrual(any(), any(), any(), any(), any());
    verify(navFeeAccrualLedger, never()).reviseFeeAccrual(any(), any(), any(), any(), any());
  }

  @Test
  void calculateFeesForNav_revisesAChangedDayByTheDifferenceToTheLedger() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    BigDecimal storedBase = new BigDecimal("1142843733.04");
    BigDecimal correctedBase = new BigDecimal("1136915291.97");
    FeeAccrual storedMgmt =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, storedBase, new BigDecimal("6418.711377"));
    FeeAccrual correctedMgmt =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, correctedBase, new BigDecimal("6385.414653"));
    FeeAccrual depotAccrual = createAccrual(TUK75, FeeType.DEPOT, day, correctedBase, ZERO);
    givenCalculated(TUK75, day, storedBase, correctedMgmt, depotAccrual);
    givenStoredRows(TUK75, day, storedMgmt, depotAccrual);
    givenLedgerEntries(TUK75, MANAGEMENT_FEE_ACCRUAL, day, "-6418.71");
    givenLedgerEntries(TUK75, DEPOT_FEE_ACCRUAL, day);

    service.calculateFeesForNav(
        TUK75, day, new FeeBases(correctedBase, correctedBase), cutoffAfter(day), null);

    verify(feeAccrualRepository).save(correctedMgmt);
    verify(feeAccrualRepository, never()).save(depotAccrual);
    verify(navFeeAccrualLedger)
        .reviseFeeAccrual(
            eq(TUK75),
            eq(day),
            eq(MANAGEMENT_FEE_ACCRUAL),
            eq(new BigDecimal("-33.30")),
            argThat(
                metadata ->
                    "FEE_ACCRUAL_REVISION".equals(metadata.get("operationType"))
                        && new BigDecimal("6418.71").equals(metadata.get("previousLedgerAmount"))
                        && new BigDecimal("6385.41").equals(metadata.get("ledgerAmount"))));
    verify(navFeeAccrualLedger, never()).recordFeeAccrual(any(), any(), any(), any(), any());
  }

  @Test
  void calculateFeesForNav_updatesTheRowButNotTheLedgerWhenTheCentAmountIsUnchanged() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    BigDecimal base = new BigDecimal("1142843733.04");
    FeeAccrual storedMgmt =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, base, new BigDecimal("6418.711377"));
    FeeAccrual recomputedMgmt =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, base, new BigDecimal("6418.713000"));
    FeeAccrual depotAccrual = createAccrual(TUK75, FeeType.DEPOT, day, base, ZERO);
    givenCalculated(TUK75, day, base, recomputedMgmt, depotAccrual);
    givenStoredRows(TUK75, day, storedMgmt, depotAccrual);
    givenLedgerEntries(TUK75, MANAGEMENT_FEE_ACCRUAL, day, "-6418.71");
    givenLedgerEntries(TUK75, DEPOT_FEE_ACCRUAL, day);

    service.calculateFeesForNav(TUK75, day, new FeeBases(base, base), cutoffAfter(day), null);

    verify(feeAccrualRepository).save(recomputedMgmt);
    verify(navFeeAccrualLedger, never()).recordFeeAccrual(any(), any(), any(), any(), any());
    verify(navFeeAccrualLedger, never()).reviseFeeAccrual(any(), any(), any(), any(), any());
  }

  @Test
  void calculateFeesForNav_reversesADayTheFundIsNoLongerCharged() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    BigDecimal base = new BigDecimal("1142843733.04");
    FeeAccrual mgmtAccrual =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, base, new BigDecimal("6418.711377"));
    FeeAccrual depotAccrual = createAccrual(TUK75, FeeType.DEPOT, day, base, ZERO);
    when(feeChargedToFundPolicy.resolverFor(TUK75, FeeType.MANAGEMENT))
        .thenReturn(alwaysCharged(TUK75, FeeType.MANAGEMENT, false));
    givenCalculated(TUK75, day, base, mgmtAccrual, depotAccrual);
    givenStoredRows(TUK75, day, mgmtAccrual, depotAccrual);
    givenLedgerEntries(TUK75, MANAGEMENT_FEE_ACCRUAL, day, "-6418.71");
    givenLedgerEntries(TUK75, DEPOT_FEE_ACCRUAL, day);

    service.calculateFeesForNav(TUK75, day, new FeeBases(base, base), cutoffAfter(day), null);

    verify(navFeeAccrualLedger)
        .reviseFeeAccrual(
            eq(TUK75), eq(day), eq(MANAGEMENT_FEE_ACCRUAL), eq(new BigDecimal("-6418.71")), any());
  }

  @Test
  void calculateFeesForNav_recordsTheLedgerEntryWhenTheRowExistsButTheLedgerIsEmpty() {
    LocalDate day = LocalDate.of(2026, 9, 1);
    BigDecimal base = new BigDecimal("1142843733.04");
    FeeAccrual mgmtAccrual =
        createAccrual(TUK75, FeeType.MANAGEMENT, day, base, new BigDecimal("6418.711377"));
    FeeAccrual depotAccrual = createAccrual(TUK75, FeeType.DEPOT, day, base, ZERO);
    givenCalculated(TUK75, day, base, mgmtAccrual, depotAccrual);
    givenStoredRows(TUK75, day, mgmtAccrual, depotAccrual);
    givenLedgerEntries(TUK75, MANAGEMENT_FEE_ACCRUAL, day);
    givenLedgerEntries(TUK75, DEPOT_FEE_ACCRUAL, day);

    service.calculateFeesForNav(TUK75, day, new FeeBases(base, base), cutoffAfter(day), null);

    verify(feeAccrualRepository, never()).save(any());
    verify(navFeeAccrualLedger)
        .recordFeeAccrual(
            eq(TUK75), eq(day), eq(MANAGEMENT_FEE_ACCRUAL), eq(new BigDecimal("6418.71")), any());
    verify(navFeeAccrualLedger, never()).reviseFeeAccrual(any(), any(), any(), any(), any());
  }

  @Test
  void calculateFeesForNav_settlesANetPositiveBalanceAsARefund() {
    LocalDate mar1 = LocalDate.of(2026, 3, 1);
    BigDecimal baseValue = new BigDecimal("12000000");
    FeeAccrual accrual1 = createAccrual(TKF100, FeeType.MANAGEMENT, mar1);
    FeeAccrual accrual2 = createAccrual(TKF100, FeeType.DEPOT, mar1);
    when(feeAccrualRepository.findLatestAccrualDate(TKF100)).thenReturn(Optional.empty());
    when(calculator1.calculate(eq(TKF100), eq(mar1), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual1);
    when(calculator2.calculate(eq(TKF100), eq(mar1), eq(new FeeBases(baseValue, baseValue))))
        .thenReturn(accrual2);
    Instant settlementCutoff = mar1.atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), settlementCutoff))
        .thenReturn(new BigDecimal("33.30"));
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            DEPOT_FEE_ACCRUAL.getAccountName(TKF100), settlementCutoff))
        .thenReturn(ZERO);
    when(feeAccrualRepository.getUnsettledAccrualByDate(eq(TKF100), any(), eq(mar1)))
        .thenReturn(Map.of());

    service.calculateFeesForNav(
        TKF100, mar1, new FeeBases(baseValue, baseValue), cutoffAfter(mar1), null);

    verify(navFeeAccrualLedger)
        .settleFeeAccrual(
            TKF100, LocalDate.of(2026, 2, 28), MANAGEMENT_FEE_ACCRUAL, new BigDecimal("-33.30"));
    verify(navFeeAccrualLedger, never())
        .settleFeeAccrual(eq(TKF100), any(), eq(DEPOT_FEE_ACCRUAL), any());
  }

  @Test
  void calculateFeesForNav_reconcilesTheWeekendRowsThatDeriveFromARevisedFriday() {
    LocalDate friday = LocalDate.of(2026, 9, 4);
    LocalDate saturday = friday.plusDays(1);
    LocalDate sunday = friday.plusDays(2);
    BigDecimal storedBase = new BigDecimal("1142028592.36");
    BigDecimal correctedBase = new BigDecimal("1140000000.00");
    BigDecimal storedDaily = new BigDecimal("6414.133190");
    BigDecimal correctedDaily = new BigDecimal("6402.739726");
    when(feeAccrualRepository.findLatestAccrualDate(TUK75))
        .thenReturn(Optional.of(LocalDate.of(2026, 9, 8)));
    when(feeAccrualRepository.findLatestBaseValue(eq(TUK75), any(FeeType.class)))
        .thenReturn(Optional.of(storedBase));
    stubZeroLedgerBalance();
    for (LocalDate day : List.of(friday, saturday, sunday)) {
      when(calculator1.calculate(eq(TUK75), eq(day), any(FeeBases.class)))
          .thenReturn(createAccrual(TUK75, FeeType.MANAGEMENT, day, correctedBase, correctedDaily));
      when(calculator2.calculate(eq(TUK75), eq(day), any(FeeBases.class)))
          .thenReturn(createAccrual(TUK75, FeeType.DEPOT, day, correctedBase, ZERO));
      givenStoredRows(
          TUK75,
          day,
          createAccrual(TUK75, FeeType.MANAGEMENT, day, storedBase, storedDaily),
          createAccrual(TUK75, FeeType.DEPOT, day, correctedBase, ZERO));
      givenLedgerEntries(TUK75, MANAGEMENT_FEE_ACCRUAL, day, "-6414.13");
      givenLedgerEntries(TUK75, DEPOT_FEE_ACCRUAL, day);
    }
    when(feeAccrualRepository.findByFundAndDateRange(TUK75, saturday, sunday))
        .thenReturn(
            List.of(
                createAccrual(TUK75, FeeType.MANAGEMENT, saturday, storedBase, storedDaily),
                createAccrual(TUK75, FeeType.DEPOT, saturday, correctedBase, ZERO),
                createAccrual(TUK75, FeeType.MANAGEMENT, sunday, storedBase, storedDaily),
                createAccrual(TUK75, FeeType.DEPOT, sunday, correctedBase, ZERO)));

    service.calculateFeesForNav(
        TUK75, friday, new FeeBases(correctedBase, correctedBase), cutoffAfter(friday), null);

    for (LocalDate day : List.of(friday, saturday, sunday)) {
      verify(feeAccrualRepository)
          .save(createAccrual(TUK75, FeeType.MANAGEMENT, day, correctedBase, correctedDaily));
      verify(navFeeAccrualLedger)
          .reviseFeeAccrual(
              eq(TUK75), eq(day), eq(MANAGEMENT_FEE_ACCRUAL), eq(new BigDecimal("-11.39")), any());
    }
    verify(calculator1, never()).calculate(eq(TUK75), eq(LocalDate.of(2026, 9, 7)), any());
    verify(navFeeAccrualLedger, never()).recordFeeAccrual(any(), any(), any(), any(), any());
  }

  private void givenCalculated(
      TulevaFund fund,
      LocalDate day,
      BigDecimal latestBase,
      FeeAccrual mgmtAccrual,
      FeeAccrual depotAccrual) {
    when(feeAccrualRepository.findLatestAccrualDate(fund)).thenReturn(Optional.of(day));
    when(feeAccrualRepository.findLatestBaseValue(eq(fund), any(FeeType.class)))
        .thenReturn(Optional.of(latestBase));
    when(calculator1.calculate(eq(fund), eq(day), any(FeeBases.class))).thenReturn(mgmtAccrual);
    when(calculator2.calculate(eq(fund), eq(day), any(FeeBases.class))).thenReturn(depotAccrual);
    stubZeroLedgerBalance();
  }

  private void givenStoredRows(
      TulevaFund fund, LocalDate day, FeeAccrual storedMgmt, FeeAccrual storedDepot) {
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(fund, day, FeeType.MANAGEMENT))
        .thenReturn(Optional.of(withId(storedMgmt)));
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(fund, day, FeeType.DEPOT))
        .thenReturn(Optional.of(withId(storedDepot)));
  }

  private void givenLedgerEntries(
      TulevaFund fund,
      ee.tuleva.onboarding.ledger.SystemAccount account,
      LocalDate day,
      String... amounts) {
    List<LedgerEntryAmount> entries =
        java.util.Arrays.stream(amounts)
            .map(
                amount ->
                    new LedgerEntryAmount(
                        UUID.randomUUID(),
                        day.atTime(9, 0).atZone(ESTONIAN_ZONE).toInstant(),
                        new BigDecimal(amount)))
            .toList();
    when(navLedgerRepository.findEntriesByTransactionTypeBetween(
            account.getAccountName(fund),
            FEE_ACCRUAL,
            day.atStartOfDay(ESTONIAN_ZONE).toInstant(),
            day.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant()))
        .thenReturn(entries);
  }

  private Instant cutoffAfter(LocalDate day) {
    return day.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
  }

  private FeeAccrual withId(FeeAccrual accrual) {
    return accrual.toBuilder().id(42L).build();
  }

  private FeeAccrual createAccrual(
      TulevaFund fund, FeeType feeType, LocalDate date, BigDecimal base, BigDecimal daily) {
    return FeeAccrual.builder()
        .fund(fund)
        .feeType(feeType)
        .accrualDate(date)
        .feeMonth(date.withDayOfMonth(1))
        .baseValue(base)
        .annualRate(new BigDecimal("0.00205"))
        .dailyAmountGross(daily)
        .daysInYear(365)
        .referenceDate(date)
        .build();
  }
}
