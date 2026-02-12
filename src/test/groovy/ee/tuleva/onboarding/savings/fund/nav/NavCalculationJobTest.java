package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static java.time.ZoneOffset.UTC;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavCalculationJobTest {

  @Mock private NavCalculationService navCalculationService;
  @Mock private NavPublisher navPublisher;
  @Mock private PublicHolidays publicHolidays;

  @InjectMocks private NavCalculationJob job;

  @BeforeEach
  void setUp() {
    Instant fixedInstant = Instant.parse("2025-01-15T14:30:00Z");
    ClockHolder.setClock(Clock.fixed(fixedInstant, UTC));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void calculateDailyNav_calculatesAndPublishesOnWorkingDay() {
    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.calculateDailyNav();

    verify(navCalculationService).calculate(TKF100, today);
    verify(navPublisher).publish(result);
  }

  @Test
  void calculateDailyNav_skipsOnNonWorkingDay() {
    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(false);

    job.calculateDailyNav();

    verifyNoInteractions(navCalculationService);
    verifyNoInteractions(navPublisher);
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
        .componentDetails(Map.of())
        .build();
  }
}
