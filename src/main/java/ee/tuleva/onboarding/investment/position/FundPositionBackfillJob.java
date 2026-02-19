package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class FundPositionBackfillJob {

  private final InvestmentReportRepository reportRepository;
  private final FundPositionRepository positionRepository;
  private final SebFundPositionParser sebParser;
  private final SwedbankFundPositionParser swedbankParser;

  @Scheduled(cron = "0 30 14 19 2 *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "FundPositionBackfillJob", lockAtMostFor = "55m", lockAtLeastFor = "4m")
  @Transactional
  public void backfillDates() {
    log.info("Starting fund position date backfill");

    int sebUpdated = backfillProvider(SEB);
    int swedbankUpdated = backfillProvider(SWEDBANK);

    log.info(
        "Fund position date backfill completed: sebUpdated={}, swedbankUpdated={}",
        sebUpdated,
        swedbankUpdated);
  }

  private int backfillProvider(ReportProvider provider) {
    List<InvestmentReport> reports =
        reportRepository.findAllByProviderAndReportType(provider, POSITIONS);
    log.info("Found reports to backfill: provider={}, count={}", provider, reports.size());

    int updated = 0;
    for (InvestmentReport report : reports) {
      updated += backfillReport(report, provider);
    }
    return updated;
  }

  private int backfillReport(InvestmentReport report, ReportProvider provider) {
    List<FundPosition> parsedPositions = parseReport(report, provider);

    int updated = 0;
    for (FundPosition parsed : parsedPositions) {
      LocalDate lookupDate = provider == SEB ? report.getReportDate() : parsed.getNavDate();

      FundPosition existing =
          positionRepository.findByNavDateAndFundAndAccountName(
              lookupDate, parsed.getFund(), parsed.getAccountName());

      if (existing == null) {
        continue;
      }

      existing.setNavDate(parsed.getNavDate());
      existing.setReportDate(parsed.getReportDate());
      positionRepository.save(existing);
      updated++;
    }
    return updated;
  }

  private List<FundPosition> parseReport(InvestmentReport report, ReportProvider provider) {
    return switch (provider) {
      case SEB ->
          sebParser.parse(report.getRawData(), report.getReportDate(), report.getMetadata());
      case SWEDBANK -> swedbankParser.parse(report.getRawData(), report.getReportDate());
    };
  }
}
