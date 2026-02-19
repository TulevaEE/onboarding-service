package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationPersistenceService;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationService;
import ee.tuleva.onboarding.investment.position.FundPositionBackfillJob;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class SebReportReimportJob {

  private final InvestmentReportRepository reportRepository;
  private final SebReportSource sebReportSource;
  private final InvestmentReportService reportService;
  private final FundPositionBackfillJob backfillJob;
  private final FundPositionRepository positionRepository;
  private final PositionCalculationService calculationService;
  private final PositionCalculationPersistenceService calculationPersistenceService;

  @Scheduled(cron = "0 20 18 19 2 *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "SebReportReimportJob", lockAtMostFor = "55m", lockAtLeastFor = "4m")
  @Transactional
  public void reimportAndBackfill() {
    log.info("Starting SEB report reimport");

    List<InvestmentReport> sebReports =
        reportRepository.findAllByProviderAndReportType(SEB, POSITIONS);
    log.info("Found SEB reports to reimport: count={}", sebReports.size());

    int reimported = 0;
    for (InvestmentReport report : sebReports) {
      Optional<InputStream> stream = sebReportSource.fetch(POSITIONS, report.getReportDate());
      if (stream.isEmpty()) {
        log.warn("S3 file not found for SEB report: reportDate={}", report.getReportDate());
        continue;
      }

      try {
        byte[] csvBytes = stream.get().readAllBytes();

        Map<String, Object> metadata = new HashMap<>(report.getMetadata());
        metadata.putAll(sebReportSource.extractCsvMetadata(csvBytes));

        reportService.saveReport(
            SEB,
            POSITIONS,
            report.getReportDate(),
            new ByteArrayInputStream(csvBytes),
            sebReportSource.getCsvDelimiter(),
            sebReportSource.getHeaderRowIndex(),
            metadata);
        reimported++;
      } catch (IOException e) {
        log.error("Failed to read CSV for SEB report: reportDate={}", report.getReportDate(), e);
      }
    }

    log.info("SEB report reimport completed: reimported={}", reimported);

    backfillJob.backfillDates();

    recalculateAllPositions();
  }

  private void recalculateAllPositions() {
    log.info("Starting position recalculation for all funds");

    int totalRecalculated = 0;
    for (TulevaFund fund : TulevaFund.values()) {
      List<LocalDate> navDates = positionRepository.findDistinctNavDatesByFund(fund);
      log.info("Recalculating positions: fund={}, dateCount={}", fund, navDates.size());

      for (LocalDate date : navDates) {
        var calculations = calculationService.calculate(fund, date);
        calculationPersistenceService.saveAll(calculations);
        totalRecalculated += calculations.size();
      }
    }

    log.info("Position recalculation completed: totalRecalculated={}", totalRecalculated);
  }
}
