package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

  @Mock private FeeCalculator calculator1;
  @Mock private FeeCalculator calculator2;
  @Mock private FeeAccrualRepository feeAccrualRepository;

  private FeeCalculationService service;

  @BeforeEach
  void setUp() {
    service = new FeeCalculationService(List.of(calculator1, calculator2), feeAccrualRepository);
  }

  @Test
  void calculateDailyFees_calculatesForAllFunds() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    FeeAccrual accrual = createAccrual(TUK75, FeeType.MANAGEMENT, date);

    when(calculator1.calculate(any(), any())).thenReturn(accrual);
    when(calculator2.calculate(any(), any())).thenReturn(accrual);

    service.calculateDailyFees(date);

    verify(calculator1, times(3)).calculate(any(), eq(date));
    verify(calculator2, times(3)).calculate(any(), eq(date));
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

    verify(calculator1, times(9)).calculate(any(), any());
    verify(calculator2, times(9)).calculate(any(), any());
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
