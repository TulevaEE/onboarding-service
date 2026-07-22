package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
  @Mock private NavReportRepository navReportRepository;
  private final PublicHolidays publicHolidays = new PublicHolidays();
  @Mock private FundValueIndexingJob fundValueIndexingJob;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PipelineTracker pipelineTracker;
  @Mock private PipelineNotifier pipelineNotifier;

  @Test
  void onNavCalculationRequested_refreshesPricesBeforeCalculating() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    InOrder inOrder = inOrder(fundValueIndexingJob, navCalculationService);
    inOrder.verify(fundValueIndexingJob).refreshForNavCalculation();
    inOrder.verify(navCalculationService).calculate(TKF100, today);
  }

  @Test
  void onNavCalculationRequested_publishesOnWorkingDay() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
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

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    verifyNoInteractions(navCalculationService);
    verifyNoInteractions(navPublisher);
    verify(eventPublisher, never()).publishEvent(any(NavCalculationCompleted.class));
  }

  @Test
  void onNavCalculationRequested_continuesWhenOneFundFails() {
    var job = jobOn("2025-01-15T09:00:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);

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
  void onNavCalculationRequested_skipsFund_whenNavAlreadyPublishedForPreviousWorkingDay() {
    var job = jobOn("2025-01-15T09:00:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(
            previousWorkingDay, TUK75.getCode()))
        .thenReturn(true);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(
            previousWorkingDay, TUK00.getCode()))
        .thenReturn(true);

    List<TulevaFund> pillar2Funds =
        getPillar2Funds().stream().filter(TulevaFund::hasNavCalculation).toList();
    job.onNavCalculationRequested(new RunNavCalculationRequested(pillar2Funds));

    verify(navReportRepository)
        .existsPublishedByNavDateAndFundCode(previousWorkingDay, TUK75.getCode());
    verify(navReportRepository)
        .existsPublishedByNavDateAndFundCode(previousWorkingDay, TUK00.getCode());
    verify(navCalculationService, never()).calculate(any(TulevaFund.class), any(LocalDate.class));
    verify(navPublisher, never()).publish(any());
    verify(fundValueIndexingJob, never()).refreshForNavCalculation();
  }

  @Test
  void onNavCalculationRequested_skipsFund_whenNavPublishedForFridayBeforeMonday() {
    var job = jobOn("2025-01-20T09:00:00Z");

    LocalDate monday = LocalDate.of(2025, 1, 20);
    LocalDate friday = LocalDate.of(2025, 1, 17);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(friday, TUK75.getCode()))
        .thenReturn(true);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(friday, TUK00.getCode()))
        .thenReturn(true);

    List<TulevaFund> pillar2Funds =
        getPillar2Funds().stream().filter(TulevaFund::hasNavCalculation).toList();
    job.onNavCalculationRequested(new RunNavCalculationRequested(pillar2Funds));

    verify(navReportRepository).existsPublishedByNavDateAndFundCode(friday, TUK75.getCode());
    verify(navReportRepository).existsPublishedByNavDateAndFundCode(friday, TUK00.getCode());
    verify(navCalculationService, never()).calculate(any(TulevaFund.class), any(LocalDate.class));
  }

  @Test
  void onNavCalculationRequested_skipsAlreadyPublishedFund() {
    var job = jobOn("2025-01-15T09:00:00Z"); // 11:00 Tallinn, pillar 2 cutoff

    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    List<TulevaFund> pillar2Funds =
        getPillar2Funds().stream().filter(TulevaFund::hasNavCalculation).toList();
    when(navReportRepository.existsPublishedByNavDateAndFundCode(
            previousWorkingDay, TUK75.getCode()))
        .thenReturn(true);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(
            previousWorkingDay, TUK00.getCode()))
        .thenReturn(false);
    when(navCalculationService.calculate(TUK00, today)).thenReturn(buildTestResult(TUK00, today));

    job.onNavCalculationRequested(new RunNavCalculationRequested(pillar2Funds));

    verify(navCalculationService, never()).calculate(TUK75, today);
    verify(navCalculationService).calculate(TUK00, today);
    verify(navPublisher, never()).publish(argThat(r -> r.fund() == TUK75));
  }

  @Test
  void onNavCalculationRequested_skipsEverythingWhenAllAlreadyPublished() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(
            previousWorkingDay, TKF100.getCode()))
        .thenReturn(true);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    verify(navCalculationService, never()).calculate(TKF100, today);
    verify(navPublisher, never()).publish(any());
    verify(fundValueIndexingJob, never()).refreshForNavCalculation();
    verify(eventPublisher, never()).publishEvent(any(NavCalculationCompleted.class));
  }

  @Test
  void onNavCalculationRequested_skipsFund_whenPublishedConcurrentlyDuringRefresh() {
    var job = jobOn("2025-01-15T09:00:00Z");

    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    when(navReportRepository.existsPublishedByNavDateAndFundCode(
            previousWorkingDay, TUK00.getCode()))
        .thenReturn(false, true); // unpublished at pipeline start, published during price refresh

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TUK00)));

    verify(fundValueIndexingJob).refreshForNavCalculation();
    verify(navCalculationService, never()).calculate(any(TulevaFund.class), any(LocalDate.class));
    verify(navPublisher, never()).publish(any());
    verify(eventPublisher, never()).publishEvent(any(NavCalculationCompleted.class));
  }

  @Test
  void onNavCalculationRequested_publishesEventWithOnlySuccessfullyCalculatedFunds() {
    var job = jobOn("2025-01-15T09:00:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);

    when(navCalculationService.calculate(TUK75, today))
        .thenThrow(new RuntimeException("Price missing"));
    when(navCalculationService.calculate(TUK00, today)).thenReturn(buildTestResult(TUK00, today));

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TUK75, TUK00)));

    verify(eventPublisher).publishEvent(new NavCalculationCompleted(List.of(TUK00)));
  }

  @Test
  void onNavCalculationRequested_continuesWhenRefreshThrows() {
    var job = jobOn("2025-01-15T14:30:00Z");

    LocalDate today = LocalDate.of(2025, 1, 15);
    doThrow(new RuntimeException("HTTP connection failed"))
        .when(fundValueIndexingJob)
        .refreshForNavCalculation();
    NavCalculationResult result = buildTestResult(today);
    when(navCalculationService.calculate(TKF100, today)).thenReturn(result);

    job.onNavCalculationRequested(new RunNavCalculationRequested(List.of(TKF100)));

    verify(navCalculationService).calculate(TKF100, today);
    verify(navPublisher).publish(result);
  }

  @Test
  void calculateDailyNavPublishesEvent() {
    var job = jobOn("2025-01-15T14:30:00Z");

    job.calculateDailyNav();

    verify(eventPublisher).publishEvent(any(RunNavCalculationRequested.class));
    verify(pipelineNotifier).sendCompleted(any());
  }

  @Test
  void calculatePillar2NavRunsPipelinePerFund() {
    var job = jobOn("2025-01-15T09:00:00Z");

    job.calculatePillar2Nav();

    for (TulevaFund fund : getPillar2Funds()) {
      verify(eventPublisher).publishEvent(new RunNavCalculationRequested(List.of(fund)));
    }
    verify(pipelineNotifier, times(getPillar2Funds().size())).sendCompleted(any());
  }

  @Test
  void calculatePillar3NavRunsPipelinePerFund() {
    var job = jobOn("2025-01-15T13:00:00Z");

    job.calculatePillar3Nav();

    for (TulevaFund fund : getPillar3Funds()) {
      verify(eventPublisher).publishEvent(new RunNavCalculationRequested(List.of(fund)));
    }
    verify(pipelineNotifier, times(getPillar3Funds().size())).sendCompleted(any());
  }

  private NavCalculationJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new NavCalculationJob(
        navCalculationService,
        navPublisher,
        navReportRepository,
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
