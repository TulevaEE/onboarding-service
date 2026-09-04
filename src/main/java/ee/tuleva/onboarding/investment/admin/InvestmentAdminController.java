package ee.tuleva.onboarding.investment.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.investment.check.tracking.PeriodType;
import ee.tuleva.onboarding.investment.check.tracking.PeriodicTdAttributionService;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService;
import ee.tuleva.onboarding.investment.position.FundPositionImportJob;
import ee.tuleva.onboarding.investment.position.FundPositionLedgerService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.ReportImportJob;
import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.investment.report.publishing.FundReportMapping;
import ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublisher;
import ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublishingResult;
import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.savings.NavFeeBackfill;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class InvestmentAdminController {

  private final AdminTokenValidator tokenValidator;
  private final FundPositionImportJob fundPositionImportJob;
  private final FundPositionLedgerService fundPositionLedgerService;
  private final FundPositionRepository fundPositionRepository;
  private final ReportImportJob reportImportJob;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final NavFeeBackfill navFeeBackfill;
  private final Optional<InvestmentReportPublisher> investmentReportPublisher;
  private final InvestmentReportDataService investmentReportDataService;
  private final InvestmentReportPdfGenerator investmentReportPdfGenerator;
  private final PeriodicTdAttributionService tdAttributionService;
  private final OcfCalculationService ocfCalculationService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @PostMapping("/reimport-positions")
  public String reimportPositions(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String provider,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate date) {

    tokenValidator.validate(token);

    ReportProvider reportProvider = ReportProvider.valueOf(provider);
    log.info("Admin triggered position reimport: provider={}, date={}", reportProvider, date);

    reportImportJob.forceImportForProviderAndDate(reportProvider, date);
    fundPositionImportJob.importForProviderAndDate(reportProvider, date);

    return "Reimported positions for " + provider + "/" + date;
  }

  @Transactional
  @PostMapping("/rerecord-positions")
  public String rerecordPositions(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate fromDate) {

    tokenValidator.validate(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    log.info("Admin triggered position re-record: fund={}, fromDate={}", fund, fromDate);
    fundPositionLedgerService.rerecordPositions(fund, fromDate);
    LocalDate latestNavDate = fundPositionRepository.findLatestNavDateByFund(fund).orElse(fromDate);
    navFeeBackfill.backfillFees(fund, fromDate, latestNavDate);

    return "Re-recorded positions and fees for " + fundCode + " from " + fromDate;
  }

  @Transactional
  @PostMapping("/rerecord-positions-from-date")
  public String rerecordPositionsFromDate(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate fromDate) {

    tokenValidator.validate(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    log.info(
        "Admin triggered date-scoped position re-record: fund={}, fromDate={}", fund, fromDate);
    fundPositionLedgerService.rerecordPositionsFromDate(fund, fromDate);
    navFeeAccrualLedger.deleteFeeAccrualsFromDate(fund, fromDate);
    feeAccrualRepository.deleteByFundFromDate(fund, fromDate);
    LocalDate latestNavDate = fundPositionRepository.findLatestNavDateByFund(fund).orElse(fromDate);
    navFeeBackfill.backfillFees(fund, fromDate, latestNavDate);

    return "Re-recorded positions and fees from " + fromDate + " for " + fundCode;
  }

  @PostMapping("/backfill-positions")
  public String backfillPositions(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DATE) LocalDate to) {

    tokenValidator.validate(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    List<LocalDate> dates =
        fundPositionRepository.findDistinctNavDatesByFund(fund).stream()
            .filter(date -> from == null || !date.isBefore(from))
            .filter(date -> to == null || !date.isAfter(to))
            .toList();
    log.info("Admin triggered position backfill: fund={}, dates={}", fund, dates.size());

    for (LocalDate date : dates) {
      fundPositionLedgerService.recordPositionsToLedger(fund, date);
    }

    return "Backfilled positions for " + fundCode + " across " + dates.size() + " dates";
  }

  @PostMapping("/backfill-fees")
  public String backfillFees(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {

    tokenValidator.validate(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    log.info("Admin triggered fee backfill: fund={}, from={}, to={}", fund, from, to);
    navFeeBackfill.backfillFees(fund, from, to);

    return "Backfilled fees for " + fundCode + " from " + from + " to " + to;
  }

  @PostMapping("/publish-investment-reports")
  public InvestmentReportPublishingResult publishInvestmentReports(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) @Nullable Integer month,
      @RequestParam(required = false) @Nullable Integer year) {
    tokenValidator.validate(token);

    var publisher =
        investmentReportPublisher.orElseThrow(
            () ->
                new ResponseStatusException(
                    SERVICE_UNAVAILABLE, "Investment report publishing not enabled"));

    var targetMonth =
        (month != null && year != null)
            ? parseReportMonth(year, month)
            : YearMonth.now(clock).minusMonths(1);

    log.info("Admin triggered investment report publishing: month={}", targetMonth);
    return publisher.publish(targetMonth);
  }

  @GetMapping("/preview-investment-report")
  public ResponseEntity<byte[]> previewInvestmentReport(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam int month,
      @RequestParam int year) {
    tokenValidator.validate(token);

    var fund = TulevaFund.fromCode(fundCode);
    var targetMonth = parseReportMonth(year, month);
    var context = investmentReportDataService.getReportData(fund, targetMonth);
    var pdfBytes = investmentReportPdfGenerator.generatePdf(context);

    var filename = FundReportMapping.forFund(fund).buildPdfFilename(targetMonth);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(filename))
        .body(pdfBytes);
  }

  @PostMapping("/td-attribution")
  public String computeTdAttribution(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) @Nullable String fundCode,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to,
      @RequestParam(defaultValue = "MONTHLY") String periodType) {

    tokenValidator.validate(token);

    var type = PeriodType.valueOf(periodType);

    if (fundCode != null) {
      var fund = TulevaFund.valueOf(fundCode);
      var result = tdAttributionService.computeAttribution(fund, from, to, type);
      return "TD attribution: %s %s to %s = %.1f bps"
          .formatted(fundCode, from, to, result.tdGeometric().multiply(BigDecimal.valueOf(10000)));
    }

    tdAttributionService.computeForAllFunds(from, to, type);
    return "TD attribution computed for all funds: %s to %s".formatted(from, to);
  }

  // The daily check writes the events the attribution reads, so a fix to the check leaves every
  // event before it carrying the old definition. Rewriting them needs a reach the fixed 7-day
  // trigger does not have.
  @PostMapping("/tracking-difference-backfill")
  public String backfillTrackingDifference(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(defaultValue = "7") int daysBack) {

    tokenValidator.validate(token);
    if (daysBack < 0) {
      throw new ResponseStatusException(BAD_REQUEST, "daysBack must not be negative: " + daysBack);
    }

    log.info("Admin triggered tracking difference backfill: daysBack={}", daysBack);
    eventPublisher.publishEvent(new RunTrackingDifferenceBackfillRequested(daysBack));
    return "Tracking difference backfill requested for last %d days".formatted(daysBack);
  }

  @PostMapping("/td-attribution-backfill")
  public String backfillTdAttribution(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(defaultValue = "6") int monthsBack) {

    tokenValidator.validate(token);
    tdAttributionService.backfillMonths(monthsBack, clock);
    return "TD attribution backfilled for last %d months".formatted(monthsBack);
  }

  @PostMapping("/ocf")
  public String calculateOcf(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) @Nullable String fundCode,
      @RequestParam(required = false) @Nullable String month) {

    tokenValidator.validate(token);

    var yearMonth = month != null ? YearMonth.parse(month) : YearMonth.now(clock).minusMonths(1);

    if (fundCode != null) {
      var fund = TulevaFund.valueOf(fundCode);
      var result = ocfCalculationService.calculateOcf(fund, yearMonth);
      return "OCF calculated: %s %s = %.2f%%"
          .formatted(fundCode, yearMonth, result.totalOcf().multiply(BigDecimal.valueOf(100)));
    }

    ocfCalculationService.calculateForAllFunds(yearMonth);
    return "OCF calculated for all funds: %s".formatted(yearMonth);
  }

  @PostMapping("/ocf-backfill")
  public String backfillOcf(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(defaultValue = "6") int monthsBack) {

    tokenValidator.validate(token);
    ocfCalculationService.backfillMonths(monthsBack, clock);
    return "OCF backfilled for last %d months".formatted(monthsBack);
  }

  private YearMonth parseReportMonth(int year, int month) {
    YearMonth reportMonth;
    try {
      reportMonth = YearMonth.of(year, month);
    } catch (DateTimeException e) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Invalid report month: year=%d, month=%d".formatted(year, month));
    }
    if (reportMonth.isAfter(YearMonth.now(clock))) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Report month is in the future: " + reportMonth);
    }
    return reportMonth;
  }
}
