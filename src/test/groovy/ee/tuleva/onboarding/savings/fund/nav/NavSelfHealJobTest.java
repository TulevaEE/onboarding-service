package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

@ExtendWith(MockitoExtension.class)
class NavSelfHealJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2025-01-15 is a Wednesday (working day). All winter times below are UTC+2 → EET Tallinn.
  private static final String WED_1325_UTC = "2025-01-15T13:25:00Z"; // 15:25 Tallinn
  private static final String WED_0910_UTC = "2025-01-15T09:10:00Z"; // 11:10 Tallinn
  private static final String WED_1200_UTC = "2025-01-15T12:00:00Z"; // 14:00 Tallinn
  private static final String WED_0800_UTC = "2025-01-15T08:00:00Z"; // 10:00 Tallinn
  private static final String SAT_1325_UTC = "2025-01-18T13:25:00Z"; // Saturday 15:25 Tallinn

  @Mock private NavReportRepository navReportRepository;
  @Mock private NavCalculationJob navCalculationJob;
  @Spy private PublicHolidays publicHolidays = new PublicHolidays();
  private final CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();

  @Test
  void healIfNeeded_firesDailyNav_whenTkf100MissingPastItsCutoff() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TKF100);

    job.healIfNeeded();

    verify(navCalculationJob).calculateDailyNav();
    verify(navCalculationJob, never()).calculatePillar2Nav();
    verify(navCalculationJob, never()).calculatePillar3Nav();
  }

  @Test
  void healIfNeeded_firesPillar3_whenTuv100MissingPastItsCutoff() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TUV100);

    job.healIfNeeded();

    verify(navCalculationJob).calculatePillar3Nav();
    verify(navCalculationJob, never()).calculateDailyNav();
    verify(navCalculationJob, never()).calculatePillar2Nav();
  }

  @Test
  void healIfNeeded_firesPillar2Once_whenTuk75Missing() {
    var job = jobOn(WED_0910_UTC); // 11:10 Tallinn, past pillar 2 cutoff 11:00

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TUK75);

    job.healIfNeeded();

    verify(navCalculationJob).calculatePillar2Nav();
    verify(navCalculationJob, never()).calculateDailyNav();
    verify(navCalculationJob, never()).calculatePillar3Nav();
  }

  @Test
  void healIfNeeded_firesPillar2Once_whenTuk00Missing() {
    var job = jobOn(WED_0910_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TUK00);

    job.healIfNeeded();

    verify(navCalculationJob).calculatePillar2Nav();
    verify(navCalculationJob, never()).calculateDailyNav();
    verify(navCalculationJob, never()).calculatePillar3Nav();
  }

  @Test
  void healIfNeeded_firesPillar2Once_whenBothTuk75AndTuk00Missing() {
    var job = jobOn(WED_0910_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TUK75);
    stubMissing(today, TUK00);

    job.healIfNeeded();

    verify(navCalculationJob).calculatePillar2Nav();
    // Invoked once even though both pillar 2 funds are missing — trigger is the whole pipeline.
    verify(navCalculationJob, never()).calculateDailyNav();
    verify(navCalculationJob, never()).calculatePillar3Nav();
  }

  @Test
  void healIfNeeded_firesAllThreePipelines_whenEverythingMissing() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubMissing(today, TKF100);
    stubMissing(today, TUK75);
    stubMissing(today, TUK00);
    stubMissing(today, TUV100);

    job.healIfNeeded();

    verify(navCalculationJob).calculateDailyNav();
    verify(navCalculationJob).calculatePillar2Nav();
    verify(navCalculationJob).calculatePillar3Nav();
  }

  @Test
  void healIfNeeded_treatsNavAsPublished_whenRowExistsForPreviousWorkingDay() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);

    job.healIfNeeded();

    verifyNoInteractions(navCalculationJob);
  }

  @Test
  void healIfNeeded_skips_whenAllPublished() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);

    job.healIfNeeded();

    verifyNoInteractions(navCalculationJob);
  }

  @Test
  void healIfNeeded_skips_onNonWorkingDay() {
    var job = jobOn(SAT_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 18);

    job.healIfNeeded();

    verifyNoInteractions(navCalculationJob);
    verifyNoInteractions(navReportRepository);
  }

  @Test
  void healIfNeeded_skipsSavingsAndPillar3_whenBefore1520() {
    var job = jobOn(WED_1200_UTC); // 14:00 Tallinn, past pillar 2 cutoff but before 15:20

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TKF100);
    stubMissing(today, TUV100);

    job.healIfNeeded();

    // Pillar 2 window is past, so pillar 2 pipeline is checked (and no-op since all published).
    // TKF100 and TUV100 are still before their cutoff — no pipeline should fire for them.
    verify(navCalculationJob, never()).calculateDailyNav();
    verify(navCalculationJob, never()).calculatePillar3Nav();
    verify(navCalculationJob, never()).calculatePillar2Nav();
  }

  @Test
  void healIfNeeded_skipsAllPipelines_whenBefore1100() {
    var job = jobOn(WED_0800_UTC); // 10:00 Tallinn, before any cutoff

    LocalDate today = LocalDate.of(2025, 1, 15);

    job.healIfNeeded();

    verifyNoInteractions(navCalculationJob);
    verifyNoInteractions(navReportRepository);
  }

  @Test
  void onApplicationReady_doesNotRunHealIfNeededOnCallerThread() {
    Instant readyInstant = Instant.parse(WED_1325_UTC);
    var job = jobOn(WED_1325_UTC);

    job.onApplicationReady();

    assertThat(taskScheduler.capturedStartTime)
        .isEqualTo(readyInstant.plus(Duration.ofSeconds(10)));
    assertThat(taskScheduler.capturedRunnable).isNotNull();
    verifyNoInteractions(navReportRepository);
    verifyNoInteractions(navCalculationJob);
    verifyNoInteractions(publicHolidays);
  }

  @Test
  void deferredRunnableHonoursNonWorkingDayGate() {
    var job = jobOn(SAT_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 18);

    job.onApplicationReady();
    taskScheduler.capturedRunnable.run();

    verifyNoInteractions(navCalculationJob);
  }

  @Test
  void deferredRunnableHonoursBetweenCutoffsGate() {
    var job = jobOn(WED_1200_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);

    job.onApplicationReady();
    taskScheduler.capturedRunnable.run();

    verifyNoInteractions(navCalculationJob);
  }

  @Test
  void deferredRunnable_invokesHealIfNeeded_whenFundMissingPastCutoff() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TKF100);

    job.onApplicationReady();
    taskScheduler.capturedRunnable.run();

    verify(navCalculationJob).calculateDailyNav();
  }

  @Test
  void scheduledPillar2Retry_invokesHealIfNeeded() {
    var job = jobOn(WED_0910_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TUK75);

    job.scheduledPillar2Retry();

    verify(navCalculationJob).calculatePillar2Nav();
  }

  @Test
  void scheduledSavingsPillar3Retry_invokesHealIfNeeded() {
    var job = jobOn(WED_1325_UTC);

    LocalDate today = LocalDate.of(2025, 1, 15);
    stubAllPublished(today);
    stubMissing(today, TUV100);

    job.scheduledSavingsPillar3Retry();

    verify(navCalculationJob).calculatePillar3Nav();
  }

  private void stubAllPublished(LocalDate today) {
    LocalDate navDate = navDateFor(today);
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(navDate, TKF100.getCode()))
        .thenReturn(List.of(new NavReportRow()));
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(navDate, TUK75.getCode()))
        .thenReturn(List.of(new NavReportRow()));
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(navDate, TUK00.getCode()))
        .thenReturn(List.of(new NavReportRow()));
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(navDate, TUV100.getCode()))
        .thenReturn(List.of(new NavReportRow()));
  }

  private void stubMissing(LocalDate today, TulevaFund fund) {
    LocalDate navDate = navDateFor(today);
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(navDate, fund.getCode()))
        .thenReturn(List.of());
  }

  private LocalDate navDateFor(LocalDate today) {
    LocalDate previousWorkingDay = today.minusDays(1);
    return previousWorkingDay;
  }

  private NavSelfHealJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new NavSelfHealJob(
        navReportRepository, navCalculationJob, publicHolidays, clock, taskScheduler);
  }

  private static class CapturingTaskScheduler implements TaskScheduler {
    Runnable capturedRunnable;
    Instant capturedStartTime;

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      this.capturedRunnable = task;
      this.capturedStartTime = startTime;
      return null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      throw new UnsupportedOperationException();
    }
  }
}
