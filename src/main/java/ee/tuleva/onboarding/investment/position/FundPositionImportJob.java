package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;

import ee.tuleva.onboarding.investment.position.parser.FundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.LocalDate;
import java.util.List;
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
public class FundPositionImportJob {

  private static final int LOOKBACK_DAYS = 7;
  private static final ReportProvider PROVIDER = ReportProvider.SWEDBANK;
  private static final ReportType REPORT_TYPE = ReportType.POSITIONS;

  private final FundPositionParser parser;
  private final FundPositionImportService importService;
  private final InvestmentReportService reportService;

  @Schedules({
    @Scheduled(cron = PARSE_MORNING, zone = TIMEZONE),
    @Scheduled(cron = PARSE_AFTERNOON, zone = TIMEZONE)
  })
  @SchedulerLock(name = "FundPositionImportJob", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void runImport() {
    LocalDate today = LocalDate.now();
    IntStream.rangeClosed(1, LOOKBACK_DAYS)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              try {
                importForDate(date);
              } catch (Exception e) {
                log.error("Import failed, continuing with next date: date={}", date, e);
              }
            });
  }

  public void importForDate(LocalDate date) {
    log.info("Starting fund position import: provider={}, date={}", PROVIDER, date);

    Optional<InvestmentReport> report = reportService.getReport(PROVIDER, REPORT_TYPE, date);
    if (report.isEmpty()) {
      log.info("No positions report in database: provider={}, date={}", PROVIDER, date);
      return;
    }

    List<FundPosition> positions = parser.parse(report.get().getRawData());
    log.info(
        "Parsed fund positions: provider={}, date={}, count={}", PROVIDER, date, positions.size());

    int imported = importService.importPositions(positions);
    log.info(
        "Fund position import completed: provider={}, date={}, imported={}, rowCount={}",
        PROVIDER,
        date,
        imported,
        report.get().getRawData().size());
  }
}
