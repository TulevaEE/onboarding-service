package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Mock private FeeCalculator calculator1;
  @Mock private FeeCalculator calculator2;
  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavFeeAccrualLedger navFeeAccrualLedger;
  @Mock private NavLedgerRepository navLedgerRepository;

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
            feeMonthResolver);
  }

  @Test
  void calculateFeesForNav_calculatesAndRecordsForPendingDays() {
    LocalDate positionReportDate = LocalDate.of(2025, 1, 13);
    BigDecimal baseValue = new BigDecimal("12000000");
    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();

    FeeAccrual mgmtAccrual = createAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate);
    FeeAccrual depotAccrual = createAccrual(TKF100, FeeType.DEPOT, positionReportDate);

    when(feeAccrualRepository.findLatestAccrualDate(TKF100))
        .thenReturn(Optional.of(LocalDate.of(2025, 1, 9)));
    when(calculator1.calculate(eq(TKF100), any(LocalDate.class), eq(baseValue)))
        .thenReturn(mgmtAccrual);
    when(calculator2.calculate(eq(TKF100), any(LocalDate.class), eq(baseValue)))
        .thenReturn(depotAccrual);
    stubZeroLedgerBalance();
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate))
        .thenReturn(new BigDecimal("400.00"));
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, FeeType.DEPOT, positionReportDate))
        .thenReturn(new BigDecimal("50.00"));

    FeeResult result =
        service.calculateFeesForNav(TKF100, positionReportDate, baseValue, feeCutoff, null);

    for (int day = 10; day <= 13; day++) {
      verify(calculator1).calculate(eq(TKF100), eq(LocalDate.of(2025, 1, day)), eq(baseValue));
      verify(calculator2).calculate(eq(TKF100), eq(LocalDate.of(2025, 1, day)), eq(baseValue));
    }
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
    when(calculator1.calculate(eq(TKF100), eq(positionReportDate), eq(baseValue)))
        .thenReturn(accrual);
    when(calculator2.calculate(eq(TKF100), eq(positionReportDate), eq(baseValue)))
        .thenReturn(accrual);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(TKF100, positionReportDate, baseValue, feeCutoff, null);

    verify(calculator1, times(1)).calculate(eq(TKF100), eq(positionReportDate), eq(baseValue));
    verify(calculator2, times(1)).calculate(eq(TKF100), eq(positionReportDate), eq(baseValue));
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
    when(calculator1.calculate(eq(TKF100), eq(positionReportDate), eq(baseValue)))
        .thenReturn(accrual);
    when(calculator2.calculate(eq(TKF100), eq(positionReportDate), eq(baseValue)))
        .thenReturn(accrual);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(TKF100, positionReportDate, baseValue, feeCutoff, securityPrices);

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
    when(calculator1.calculate(eq(TKF100), eq(mar1), eq(baseValue))).thenReturn(accrual1);
    when(calculator2.calculate(eq(TKF100), eq(mar1), eq(baseValue))).thenReturn(accrual2);

    Instant settlementCutoff =
        LocalDate.of(2026, 3, 1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(TKF100), settlementCutoff))
        .thenReturn(new BigDecimal("-1500.00"));
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            DEPOT_FEE_ACCRUAL.getAccountName(TKF100), settlementCutoff))
        .thenReturn(new BigDecimal("-200.00"));
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, FeeType.MANAGEMENT, mar1))
        .thenReturn(ZERO);
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, FeeType.DEPOT, mar1)).thenReturn(ZERO);

    service.calculateFeesForNav(TKF100, mar1, baseValue, feeCutoff, null);

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
    when(calculator1.calculate(eq(TKF100), eq(feb15), eq(baseValue))).thenReturn(accrual1);
    when(calculator2.calculate(eq(TKF100), eq(feb15), eq(baseValue))).thenReturn(accrual2);
    stubZeroLedgerBalance();

    service.calculateFeesForNav(TKF100, feb15, baseValue, feeCutoff, null);

    verify(navFeeAccrualLedger, never()).settleFeeAccrual(any(), any(), any(), any());
  }

  private void stubZeroLedgerBalance() {
    when(navLedgerRepository.getSystemAccountBalanceBefore(any(), any(Instant.class)))
        .thenReturn(ZERO);
    when(feeAccrualRepository.getUnsettledAccrual(any(), any(), any(LocalDate.class)))
        .thenReturn(ZERO);
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
    when(calculator1.calculate(eq(TKF100), eq(positionReportDate), eq(baseValue)))
        .thenReturn(mgmtAccrual);
    when(calculator2.calculate(eq(TKF100), eq(positionReportDate), eq(baseValue)))
        .thenReturn(depotAccrual);
    stubZeroLedgerBalance();
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, FeeType.MANAGEMENT, positionReportDate))
        .thenReturn(new BigDecimal("400.12"));
    when(feeAccrualRepository.getUnsettledAccrual(TKF100, FeeType.DEPOT, positionReportDate))
        .thenReturn(new BigDecimal("50.34"));

    FeeResult result =
        service.calculateFeesForNav(TKF100, positionReportDate, baseValue, feeCutoff, null);

    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("400.12");
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo("50.34");
  }

  private FeeAccrual createAccrual(TulevaFund fund, FeeType feeType, LocalDate date) {
    return FeeAccrual.builder()
        .fund(fund)
        .feeType(feeType)
        .accrualDate(date)
        .feeMonth(date.withDayOfMonth(1))
        .baseValue(ZERO)
        .annualRate(ZERO)
        .dailyAmountNet(ZERO)
        .dailyAmountGross(ZERO)
        .daysInYear(365)
        .build();
  }
}
