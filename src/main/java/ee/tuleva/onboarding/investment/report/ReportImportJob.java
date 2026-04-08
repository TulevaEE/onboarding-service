package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;
import static ee.tuleva.onboarding.investment.event.PipelineStep.REPORT_IMPORT;

import ee.tuleva.onboarding.investment.event.PipelineNotifier;
import ee.tuleva.onboarding.investment.event.PipelineRun;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.RunReportImportRequested;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportImportJob {

  private static final int LOOKBACK_DAYS = 7;
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final List<ReportSource> sources;
  private final InvestmentReportService reportService;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;
  private final PipelineTracker pipelineTracker;
  private final PipelineNotifier pipelineNotifier;

  @Schedules({
    @Scheduled(cron = IMPORT_MORNING, zone = TIMEZONE),
    @Scheduled(cron = IMPORT_AFTERNOON, zone = TIMEZONE)
  })
  @SchedulerLock(name = "ReportImportJob", lockAtMostFor = "55m", lockAtLeastFor = "4m")
  public void schedule() {
    var trigger = "cron:" + LocalTime.now(clock.withZone(ESTONIAN_ZONE));
    pipelineTracker.start(PipelineRun.PipelineType.IMPORT, trigger);
    try {
      eventPublisher.publishEvent(new RunReportImportRequested());
    } finally {
      pipelineNotifier.sendCompleted(pipelineTracker.current());
      pipelineTracker.clear();
    }
  }

  @EventListener
  public void onReportImportRequested(RunReportImportRequested event) {
    pipelineTracker.stepStarted(REPORT_IMPORT);
    runImport();
    pipelineTracker.stepCompleted(REPORT_IMPORT);
    eventPublisher.publishEvent(new ReportImportCompleted());
  }

  public void runImport() {
    LocalDate today = LocalDate.now(clock);
    IntStream.rangeClosed(1, LOOKBACK_DAYS)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              try {
                importForDate(date);
              } catch (Exception e) {
                log.error("Report import failed, continuing with next date: date={}", date, e);
              }
            });
  }

  public void importForDate(LocalDate date) {
    log.info("Starting report import: date={}", date);

    for (ReportSource source : sources) {
      for (ReportType reportType : source.getSupportedReportTypes()) {
        importReport(source, reportType, date);
      }
    }
  }

  public void importForProviderAndDate(ReportProvider provider, LocalDate date) {
    log.info("Starting report import: provider={}, date={}", provider, date);

    sources.stream()
        .filter(source -> source.getProvider() == provider)
        .forEach(
            source -> {
              for (ReportType reportType : source.getSupportedReportTypes()) {
                importReport(source, reportType, date);
              }
            });
  }

  public void forceImportForProviderAndDate(ReportProvider provider, LocalDate date) {
    log.info("Force re-importing report: provider={}, date={}", provider, date);

    sources.stream()
        .filter(source -> source.getProvider() == provider)
        .forEach(
            source -> {
              for (ReportType reportType : source.getSupportedReportTypes()) {
                forceImportReport(source, reportType, date);
              }
            });
  }

  private void forceImportReport(ReportSource source, ReportType reportType, LocalDate date) {
    Optional<InputStream> stream = source.fetch(reportType, date);
    if (stream.isEmpty()) {
      return;
    }
    saveReport(source, reportType, date, stream.get());
  }

  private void importReport(ReportSource source, ReportType reportType, LocalDate date) {
    ReportProvider provider = source.getProvider();

    Optional<InvestmentReport> existing = reportService.getReport(provider, reportType, date);
    if (existing.isPresent() && !shouldRefresh(source, reportType, date, existing.get())) {
      log.info(
          "Report up to date: provider={}, reportType={}, date={}", provider, reportType, date);
      return;
    }

    Optional<InputStream> stream = source.fetch(reportType, date);
    if (stream.isEmpty()) {
      return;
    }

    saveReport(source, reportType, date, stream.get());
  }

  private void saveReport(
      ReportSource source, ReportType reportType, LocalDate date, InputStream csvStream) {
    ReportProvider provider = source.getProvider();
    try (csvStream) {
      byte[] csvBytes = csvStream.readAllBytes();

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("s3Bucket", source.getBucket());
      metadata.put("s3Key", source.getKey(reportType, date));
      metadata.put("importTimestamp", clock.instant().toString());
      source
          .getLastModified(reportType, date)
          .ifPresent(instant -> metadata.put("s3LastModified", instant.toString()));
      metadata.putAll(source.extractCsvMetadata(csvBytes));

      InvestmentReport report =
          reportService.saveReport(
              provider,
              reportType,
              date,
              new ByteArrayInputStream(csvBytes),
              source.getCsvDelimiter(),
              source.getHeaderRowIndex(),
              metadata);

      log.info(
          "Report import completed: provider={}, reportType={}, date={}, rowCount={}",
          provider,
          reportType,
          date,
          report.getRawData().size());

    } catch (IOException e) {
      log.error(
          "Report import failed: provider={}, reportType={}, date={}",
          provider,
          reportType,
          date,
          e);
      throw new RuntimeException(
          "Report import failed: provider="
              + provider
              + ", reportType="
              + reportType
              + ", date="
              + date,
          e);
    }
  }

  private boolean shouldRefresh(
      ReportSource source, ReportType reportType, LocalDate date, InvestmentReport existing) {
    if (date.isBefore(LocalDate.now(clock).minusDays(LOOKBACK_DAYS))) {
      return false;
    }
    Optional<Instant> s3LastModified = source.getLastModified(reportType, date);
    if (s3LastModified.isEmpty()) {
      return false;
    }
    Object stored = existing.getMetadata().get("s3LastModified");
    return stored == null || !s3LastModified.get().toString().equals(stored.toString());
  }
}
