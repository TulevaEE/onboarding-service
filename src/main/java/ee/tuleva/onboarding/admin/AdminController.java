package ee.tuleva.onboarding.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceSynchronizer;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
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
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.ParentChildLinkRegistrationService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final ApplicationEventPublisher eventPublisher;
  private final SavingsFundLedger savingsFundLedger;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavCalculationService navCalculationService;
  private final NavPublisher navPublisher;
  private final FundBalanceSynchronizer fundBalanceSynchronizer;
  private final FundPositionLedgerService fundPositionLedgerService;
  private final FundPositionRepository fundPositionRepository;
  private final ReportImportJob reportImportJob;
  private final FundPositionImportJob fundPositionImportJob;
  private final RedemptionBatchJob redemptionBatchJob;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final Clock clock;
  private final Optional<InvestmentReportPublisher> investmentReportPublisher;
  private final InvestmentReportDataService investmentReportDataService;
  private final InvestmentReportPdfGenerator investmentReportPdfGenerator;

  @Value("${admin.api-token:}")
  private String adminApiToken;

  @Value("${admin.ops-token:}")
  private String opsToken;

  @PostMapping("/fetch-seb-history")
  public String fetchSebHistory(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to,
      @RequestParam(required = false) BankAccountType account) {

    validateToken(token);

    var accounts = account != null ? List.of(account) : Arrays.asList(BankAccountType.values());

    log.info("Admin triggered SEB history fetch: from={}, to={}, accounts={}", from, to, accounts);

    for (BankAccountType bankAccount : accounts) {
      log.info("Fetching SEB history: account={}", bankAccount);
      eventPublisher.publishEvent(new FetchSebHistoricTransactionsRequested(bankAccount, from, to));
    }

    return "Fetched SEB history for " + accounts + " from " + from + " to " + to;
  }

  @Transactional
  @PostMapping("/adjustments")
  public List<Map<String, String>> createAdjustments(
      @RequestHeader("X-Admin-Token") String token, @RequestBody List<AdjustmentRequest> requests) {

    validateToken(token);

    log.info("Admin triggered adjustments: count={}", requests.size());

    var results =
        requests.stream()
            .map(
                request -> {
                  var transaction =
                      savingsFundLedger.recordAdjustment(
                          request.debitAccount(),
                          request.debitParty(),
                          request.creditAccount(),
                          request.creditParty(),
                          request.amount(),
                          request.externalReference(),
                          request.description());
                  log.info(
                      "Adjustment recorded: transactionId={}, debitAccount={}, creditAccount={}, amount={}, description={}",
                      transaction.getId(),
                      request.debitAccount(),
                      request.creditAccount(),
                      request.amount(),
                      request.description());
                  return Map.of("transactionId", transaction.getId().toString());
                })
            .toList();

    log.info("All adjustments completed: count={}", results.size());
    return results;
  }

  @PostMapping("/calculate-nav")
  public NavCalculationResult calculateNav(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(defaultValue = "TKF100") String fundCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date,
      @RequestParam(defaultValue = "false") boolean publish) {

    validateTokenWithOpsAccess(token);

    LocalDate calculationDate = date != null ? date : LocalDate.now(clock);

    log.info(
        "Admin triggered NAV calculation: fund={}, date={}, publish={}",
        fundCode,
        calculationDate,
        publish);

    NavCalculationResult result = navCalculationService.calculate(fundCode, calculationDate);

    if (publish) {
      navPublisher.publish(result);
      log.info("NAV published: date={}, navPerUnit={}", calculationDate, result.navPerUnit());
    }

    return result;
  }

  @PostMapping("/backfill-fees")
  public String backfillFees(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {

    validateToken(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    log.info("Admin triggered fee backfill: fund={}, from={}, to={}", fund, from, to);
    navCalculationService.backfillFees(fund, from, to);

    return "Backfilled fees for " + fundCode + " from " + from + " to " + to;
  }

  @PostMapping("/backfill-unit-counts")
  public String backfillUnitCounts(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {

    validateToken(token);

    log.info("Admin triggered unit count backfill: from={}, to={}", from, to);
    fundBalanceSynchronizer.backfillUnitCounts(from, to);

    return "Backfilled unit counts from " + from + " to " + to;
  }

  @PostMapping("/reimport-positions")
  public String reimportPositions(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String provider,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate date) {

    validateToken(token);

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

    validateToken(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    log.info("Admin triggered position re-record: fund={}, fromDate={}", fund, fromDate);
    fundPositionLedgerService.rerecordPositions(fund, fromDate);
    LocalDate latestNavDate = fundPositionRepository.findLatestNavDateByFund(fund).orElse(fromDate);
    navCalculationService.backfillFees(fund, fromDate, latestNavDate);

    return "Re-recorded positions and fees for " + fundCode + " from " + fromDate;
  }

  @Transactional
  @PostMapping("/rerecord-positions-from-date")
  public String rerecordPositionsFromDate(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate fromDate) {

    validateToken(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    log.info(
        "Admin triggered date-scoped position re-record: fund={}, fromDate={}", fund, fromDate);
    fundPositionLedgerService.rerecordPositionsFromDate(fund, fromDate);
    navFeeAccrualLedger.deleteFeeAccrualsFromDate(fund, fromDate);
    feeAccrualRepository.deleteByFundFromDate(fund, fromDate);
    LocalDate latestNavDate = fundPositionRepository.findLatestNavDateByFund(fund).orElse(fromDate);
    navCalculationService.backfillFees(fund, fromDate, latestNavDate);

    return "Re-recorded positions and fees from " + fromDate + " for " + fundCode;
  }

  @PostMapping("/backfill-positions")
  public String backfillPositions(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate to) {

    validateToken(token);

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

  @PostMapping("/redemptions/{id}/retry-payout")
  public String retryRedemptionPayout(
      @RequestHeader("X-Admin-Token") String token, @PathVariable UUID id) {

    validateToken(token);

    log.info("Admin triggered redemption payout retry: id={}", id);
    redemptionBatchJob.retryFailedPayout(id);

    return "Retried redemption payout for " + id;
  }

  @PostMapping("/whitelist-company")
  public String whitelistCompany(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String registryCode,
      @RequestParam(defaultValue = "false") boolean override) {

    validateTokenWithOpsAccess(token);
    savingsFundOnboardingService.whitelistLegalEntity(registryCode, override);

    return "Whitelisted company: registryCode=" + registryCode;
  }

  @PostMapping("/parent-child-link")
  public String createParentChildLink(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateParentChildLinkRequest request) {

    validateTokenWithOpsAccess(token);
    parentChildLinkRegistrationService.register(
        request.parentCode(),
        request.childCode(),
        request.childFirstName(),
        request.childLastName(),
        request.relationshipType());

    return "Created parent-child link: parentCode="
        + request.parentCode()
        + ", childCode="
        + request.childCode();
  }

  @PostMapping("/blackrock-adjustment")
  public BlackrockAdjustmentResult recordBlackrockAdjustment(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam BigDecimal amount,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate date) {

    validateTokenWithOpsAccess(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_UP);
    log.info(
        "Admin triggered BlackRock adjustment: fund={}, date={}, targetBalance={}",
        fund,
        date,
        roundedAmount);

    return navFeeAccrualLedger.recordBlackrockAdjustment(fund, date, roundedAmount);
  }

  @PostMapping("/publish-investment-reports")
  public InvestmentReportPublishingResult publishInvestmentReports(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer year) {
    validateToken(token);

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
    validateToken(token);

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

  private void validateTokenWithOpsAccess(String token) {
    boolean matchesAdmin = !adminApiToken.isBlank() && adminApiToken.equals(token);
    boolean matchesOps = !opsToken.isBlank() && opsToken.equals(token);
    if (!matchesAdmin && !matchesOps) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
    }
  }

  private void validateToken(String token) {
    if (adminApiToken.isBlank()) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Admin API not configured");
    }
    if (!adminApiToken.equals(token)) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
    }
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
