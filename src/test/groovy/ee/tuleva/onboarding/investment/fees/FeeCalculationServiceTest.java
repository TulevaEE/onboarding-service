package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static java.math.BigDecimal.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

  private static final int FUND_COUNT = TulevaFund.values().length;

  @Mock private FeeCalculator calculator1;
  @Mock private FeeCalculator calculator2;
  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavFeeAccrualLedger navFeeAccrualLedger;

  private FeeCalculationService service;

  @BeforeEach
  void setUp() {
    service =
        new FeeCalculationService(
            List.of(calculator1, calculator2), feeAccrualRepository, navFeeAccrualLedger);
  }

  @Test
  void calculateDailyFees_calculatesForAllFunds() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    FeeAccrual accrual = createAccrual(TUK75, FeeType.MANAGEMENT, date);

    when(calculator1.calculate(any(), any())).thenReturn(accrual);
    when(calculator2.calculate(any(), any())).thenReturn(accrual);

    service.calculateDailyFees(date);

    verify(calculator1, times(FUND_COUNT)).calculate(any(), eq(date));
    verify(calculator2, times(FUND_COUNT)).calculate(any(), eq(date));
  }

  @Test
  void calculateDailyFeesForFund_savesAllAccruals() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    FeeAccrual accrual1 = createAccrual(TUV100, FeeType.MANAGEMENT, date);
    FeeAccrual accrual2 = createAccrual(TUV100, FeeType.DEPOT, date);

    when(calculator1.calculate(TUV100, date)).thenReturn(accrual1);
    when(calculator2.calculate(TUV100, date)).thenReturn(accrual2);

    service.calculateDailyFeesForFund(TUV100, date);

    verify(feeAccrualRepository, times(2)).save(any(FeeAccrual.class));
  }

  @Test
  void backfillFees_calculatesForEachDayInRange() {
    LocalDate startDate = LocalDate.of(2025, 1, 1);
    LocalDate endDate = LocalDate.of(2025, 1, 3);
    FeeAccrual accrual = createAccrual(TUK75, FeeType.MANAGEMENT, startDate);

    when(calculator1.calculate(any(), any())).thenReturn(accrual);
    when(calculator2.calculate(any(), any())).thenReturn(accrual);

    service.backfillFees(startDate, endDate);

    int daysInRange = 3;
    int expectedCalls = FUND_COUNT * daysInRange;
    verify(calculator1, times(expectedCalls)).calculate(any(), any());
    verify(calculator2, times(expectedCalls)).calculate(any(), any());
  }

  @Test
  void calculateDailyFeesForFund_recordsToLedgerForEachFeeType() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    FeeAccrual accrual1 = createAccrual(TUV100, FeeType.MANAGEMENT, date);
    FeeAccrual accrual2 = createAccrual(TUV100, FeeType.DEPOT, date);

    when(calculator1.calculate(TUV100, date)).thenReturn(accrual1);
    when(calculator2.calculate(TUV100, date)).thenReturn(accrual2);

    service.calculateDailyFeesForFund(TUV100, date);

    verify(navFeeAccrualLedger)
        .recordFeeAccrual(eq("TUV100"), eq(date), eq(MANAGEMENT_FEE_ACCRUAL), any());
    verify(navFeeAccrualLedger)
        .recordFeeAccrual(eq("TUV100"), eq(date), eq(DEPOT_FEE_ACCRUAL), any());
  }

  @Test
  void calculateDailyFees_recordsToLedgerForAllFunds() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    FeeAccrual accrual = createAccrual(TUK75, FeeType.MANAGEMENT, date);

    when(calculator1.calculate(any(), any())).thenReturn(accrual);
    when(calculator2.calculate(any(), any())).thenReturn(accrual);

    service.calculateDailyFees(date);

    int totalCalls = FUND_COUNT * 2; // 2 calculators per fund
    verify(navFeeAccrualLedger, times(totalCalls)).recordFeeAccrual(any(), eq(date), any(), any());
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
