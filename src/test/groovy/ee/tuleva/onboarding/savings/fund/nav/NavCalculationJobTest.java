package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.PipelineNotifier;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NavCalculationJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Mock private NavCalculationService navCalculationService;
  @Mock private NavPublisher navPublisher;
  @Mock private PublicHolidays publicHolidays;
  @Mock private FundValueIndexingJob fundValueIndexingJob;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PipelineTracker pipelineTracker;
  @Mock private PipelineNotifier pipelineNotifier;

  @Test
  void onNavCalculationRequested_refreshesPricesBeforeCalculating() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    InOrder inOrder = inOrder(fundValueIndexingJob, navCalculationService);
    inOrder.verify(fundValueIndexingJob).refreshAll();
    inOrder.verify(navCalculationService).calculate(TKF100, today);
  }

  @Test
  void onNavCalculationRequested_publishesOnWorkingDay() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    verify(navCalculationService).calculate(TKF100, today);
    verify(navPublisher).publish(result);
    verify(eventPublisher).publishEvent(any(NavCalculationCompleted.class));
  }

  @Test
  void onNavCalculationRequested_skipsOnNonWorkingDay() {
    var job = jobOn("2025-01-18T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 18);
    when(publicHolidays.isWorkingDay(today)).thenReturn(false);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    verifyNoInteractions(navCalculationService);
    verifyNoInteractions(navPublisher);
  }

  @Test
  void onNavCalculationRequested_continuesWhenOneFundFails() {
    var job = jobOn("2025-01-15T09:00:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);

    List<TulevaFund> pillar2Funds =
        getPillar2Funds().stream().filter(TulevaFund::hasNavCalculation).toList();

    TulevaFund firstFund = pillar2Funds.getFirst();
    when(navCalculationService.calculate(firstFund, today))
        .thenThrow(new RuntimeException("Price missing"));

    for (int i = 1; i < pillar2Funds.size(); i++) {
      var result = buildTestResult(pillar2Funds.get(i), today);
      when(navCalculationService.calculate(pillar2Funds.get(i), today)).thenReturn(result);
    }

    job.onNavCalculationRequested(new RunNavCalculationRequested(pillar2Funds));

    for (int i = 1; i < pillar2Funds.size(); i++) {
      verify(navCalculationService).calculate(pillar2Funds.get(i), today);
    }
  }

  @Test
  void onNavCalculationRequested_calculatesAllPillar2Funds() {
    var job = jobOn("2025-01-15T09:00:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);

    getPillar2Funds().stream()
        .filter(TulevaFund::hasNavCalculation)
        .forEach(
            fund -> {
              var result = buildTestResult(fund, today);
              when(navCalculationService.calculate(fund, today)).thenReturn(result);
            });

    job.onNavCalculationRequested(new RunNavCalculationRequested(getPillar2Funds()));

    getPillar2Funds().stream()
        .filter(TulevaFund::hasNavCalculation)
        .forEach(fund -> verify(navCalculationService).calculate(fund, today));
  }

  @Test
  void onNavCalculationRequested_calculatesAllPillar3Funds() {
    var job = jobOn("2025-01-15T13:00:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);

    getPillar3Funds().stream()
        .filter(TulevaFund::hasNavCalculation)
        .forEach(
            fund -> {
              var result = buildTestResult(fund, today);
              when(navCalculationService.calculate(fund, today)).thenReturn(result);
            });

    job.onNavCalculationRequested(new RunNavCalculationRequested(getPillar3Funds()));

    getPillar3Funds().stream()
        .filter(TulevaFund::hasNavCalculation)
        .forEach(fund -> verify(navCalculationService).calculate(fund, today));
  }

  @Test
  void onNavCalculationRequested_continuesWhenRefreshAllThrows() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    when(publicHolidays.isWorkingDay(today)).thenReturn(true);
    doThrow(new RuntimeException("FTP connection failed")).when(fundValueIndexingJob).refreshAll();
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    verify(navCalculationService).calculate(TKF100, today);
    verify(navPublisher).publish(result);
  }

  @Test
  void scheduleMethodsPublishEvents() {
    var job = jobOn("2025-01-15T14:30:00Z");

    job.calculateDailyNav();

    verify(eventPublisher).publishEvent(any(RunNavCalculationRequested.class));
    verify(pipelineNotifier).sendStarted(any());
    verify(pipelineNotifier).sendCompleted(any());
  }

  private NavCalculationJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new NavCalculationJob(
        navCalculationService,
        navPublisher,
        publicHolidays,
        fundValueIndexingJob,
        clock,
        eventPublisher,
        pipelineTracker,
        pipelineNotifier);
  }

  private NavCalculationResult buildTestResult(LocalDate date) {
    return buildTestResult(TKF100, date);
  }

  private NavCalculationResult buildTestResult(TulevaFund fund, LocalDate date) {
    return NavCalculationResult.builder()
        .fund(fund)
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
