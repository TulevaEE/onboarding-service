package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavCalculationJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Mock private NavCalculationService navCalculationService;
  @Mock private NavNotifier navNotifier;
  @Mock private PublicHolidays publicHolidays;

  @Test
  void calculateDailyNav_notifiesOnWorkingDay() {
    Clock clock = Clock.fixed(Instant.parse("2025-01-15T14:30:00Z"), TALLINN);
    var job = new NavCalculationJob(navCalculationService, navNotifier, publicHolidays, clock);

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.calculateDailyNav();

    verify(navCalculationService).calculate(TKF100, today);
    verify(navNotifier).notify(result);
  }

  @Test
  void calculateDailyNav_skipsOnNonWorkingDay() {
    Clock clock = Clock.fixed(Instant.parse("2025-01-18T14:30:00Z"), TALLINN);
    var job = new NavCalculationJob(navCalculationService, navNotifier, publicHolidays, clock);

    LocalDate today = LocalDate.of(2025, 1, 18);
    when(publicHolidays.isWorkingDay(today)).thenReturn(false);

    job.calculateDailyNav();

    verifyNoInteractions(navCalculationService);
    verifyNoInteractions(navNotifier);
  }

  private NavCalculationResult buildTestResult(LocalDate date) {
    return NavCalculationResult.builder()
        .fund(TKF100)
        .calculationDate(date)
        .securitiesValue(new BigDecimal("900000.00"))
        .cashPosition(new BigDecimal("50000.00"))
        .receivables(BigDecimal.ZERO)
        .pendingSubscriptions(BigDecimal.ZERO)
        .pendingRedemptions(BigDecimal.ZERO)
        .managementFeeAccrual(BigDecimal.ZERO)
        .depotFeeAccrual(BigDecimal.ZERO)
        .payables(BigDecimal.ZERO)
        .blackrockAdjustment(BigDecimal.ZERO)
        .aum(new BigDecimal("950000.00"))
        .unitsOutstanding(new BigDecimal("100000.00000"))
        .navPerUnit(new BigDecimal("9.50000"))
        .positionReportDate(date)
        .priceDate(date)
        .calculatedAt(Instant.now())
        .securitiesDetail(List.of())
        .build();
  }
}
