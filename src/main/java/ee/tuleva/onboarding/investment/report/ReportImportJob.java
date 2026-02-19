package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class ReportImportJob {

  private static final int LOOKBACK_DAYS = 7;

  private final List<ReportSource> sources;
  private final InvestmentReportService reportService;

  @Schedules({
    @Scheduled(cron = IMPORT_MORNING, zone = TIMEZONE),
    @Scheduled(cron = IMPORT_AFTERNOON, zone = TIMEZONE)
  })
  @SchedulerLock(name = "ReportImportJob", lockAtMostFor = "55m", lockAtLeastFor = "4m")
  public void runImport() {
    LocalDate today = LocalDate.now();
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

  private void importReport(ReportSource source, ReportType reportType, LocalDate date) {
    ReportProvider provider = source.getProvider();

    Optional<InvestmentReport> existing = reportService.getReport(provider, reportType, date);
    if (existing.isPresent()) {
      log.info(
          "Report already in database: provider={}, reportType={}, date={}",
          provider,
          reportType,
          date);
      return;
    }

    Optional<InputStream> stream = source.fetch(reportType, date);
    if (stream.isEmpty()) {
      return;
    }

    try (InputStream csvStream = stream.get()) {
      byte[] csvBytes = csvStream.readAllBytes();

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("s3Bucket", source.getBucket());
      metadata.put("s3Key", source.getKey(reportType, date));
      metadata.put("importTimestamp", Instant.now().toString());
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
}
