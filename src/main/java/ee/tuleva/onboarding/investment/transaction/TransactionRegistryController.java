package ee.tuleva.onboarding.investment.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.EpisReportIngestionResult;
import ee.tuleva.onboarding.investment.epis.EpisReportIngestionService;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaStatus;
import ee.tuleva.onboarding.investment.epis.PevaRavaStatusService;
import ee.tuleva.onboarding.investment.epis.R16FundStatus;
import ee.tuleva.onboarding.investment.epis.R16StatusService;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarning;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarningService;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationRequested;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.transaction.ingest.FtConfirmationVerificationService;
import ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
@NullMarked
public class TransactionRegistryController {

  private final ApplicationEventPublisher eventPublisher;
  private final TransactionAdminService adminService;
  private final FtConfirmationVerificationService ftConfirmationVerificationService;
  private final HistoricalRegistryImportService historicalRegistryImportService;
  private final EpisReportIngestionService episReportIngestionService;
  private final PevaRavaStatusService pevaRavaStatusService;
  private final R45ReportService r45ReportService;
  private final R16StatusService r16StatusService;
  private final SettlementTimingWarningService settlementTimingWarningService;

  @Value("${admin.api-token:}")
  private String adminApiToken = "";

  @PostMapping("/transaction-registry/match")
  @ResponseStatus(ACCEPTED)
  public String triggerExecutionMatching(@RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    log.info("Admin triggered SEB pending transaction reconciliation");
    eventPublisher.publishEvent(new RunSebPendingTransactionReconciliationRequested());

    return "Triggered SEB pending transaction reconciliation";
  }

  @GetMapping("/dashboard/daily-summary")
  public TransactionDailySummary dailySummary(@RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    return adminService.dailySummary();
  }

  @PostMapping("/transaction-registry/ft-confirmation")
  public FtConfirmationResult verifyFtConfirmation(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody FtConfirmation confirmation) {

    validateToken(token);

    log.info(
        "Admin submitted FT confirmation for verification: fund={}, isin={}, tradeDate={}",
        confirmation.fund(),
        confirmation.isin(),
        confirmation.tradeDate());
    return ftConfirmationVerificationService.verify(confirmation);
  }

  @PostMapping("/transaction-registry/ft-confirmations")
  public List<FtConfirmationBatchResult> verifyFtConfirmations(
      @RequestHeader("X-Admin-Token") String token,
      @RequestHeader(name = "X-Admin-Actor", required = false, defaultValue = "admin") String actor,
      @Valid @RequestBody List<@NotNull @Valid FtConfirmation> confirmations) {

    validateToken(token);

    log.info(
        "Admin submitted FT confirmations batch for verification: count={}, actor={}",
        confirmations.size(),
        actor);
    return ftConfirmationVerificationService.verifyAll(confirmations, actor);
  }

  @PostMapping(value = "/transaction-registry/import-history", consumes = TEXT_PLAIN_VALUE)
  public HistoricalImportResult importHistory(
      @RequestHeader("X-Admin-Token") String token, @RequestBody String csv) {

    validateToken(token);

    log.info("Admin triggered historical registry import: contentLength={}", csv.length());
    return historicalRegistryImportService.importCsv(csv);
  }

  @PostMapping(value = "/transaction-registry/import-history", consumes = MULTIPART_FORM_DATA_VALUE)
  public HistoricalImportResult importHistoryFile(
      @RequestHeader("X-Admin-Token") String token, @RequestParam("file") MultipartFile file)
      throws IOException {

    validateToken(token);

    log.info(
        "Admin triggered historical registry import from file: fileName={}, size={}",
        file.getOriginalFilename(),
        file.getSize());
    return historicalRegistryImportService.importCsv(new String(file.getBytes(), UTF_8));
  }

  @PostMapping(value = "/transaction-registry/import-report", consumes = TEXT_PLAIN_VALUE)
  public EpisReportIngestionResult importEpisReport(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam("type") ReportType type,
      @RequestBody String csv) {

    validateToken(token);

    log.info("Admin triggered EPIS report import: type={}, contentLength={}", type, csv.length());
    return episReportIngestionService.ingestReport(type, csv);
  }

  @PostMapping(value = "/transaction-registry/import-report", consumes = MULTIPART_FORM_DATA_VALUE)
  public EpisReportIngestionResult importEpisReportFile(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam("type") ReportType type,
      @RequestParam("file") MultipartFile file)
      throws IOException {

    validateToken(token);

    log.info(
        "Admin triggered EPIS report import from file: type={}, fileName={}, size={}",
        type,
        file.getOriginalFilename(),
        file.getSize());
    return episReportIngestionService.ingestReport(type, new String(file.getBytes(), UTF_8));
  }

  @GetMapping("/transaction-registry/peva-rava/status")
  public PevaRavaStatus pevaRavaStatus(@RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    return pevaRavaStatusService.status();
  }

  @PostMapping("/transaction-registry/peva-rava/calculate")
  public Map<TulevaFund, PevaRavaFlows> recalculatePevaRavaFlows(
      @RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    log.info("Admin triggered PEVA/RAVA flow recalculation");
    return pevaRavaStatusService.recalculate();
  }

  @GetMapping("/transaction-registry/r45/latest")
  public Map<TulevaFund, R45Result> latestR45Flows(@RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    return r45ReportService.getLatestFlows();
  }

  @GetMapping("/transaction-registry/r16/status")
  public List<R16FundStatus> r16Status(@RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    return r16StatusService.status();
  }

  @GetMapping("/dashboard/settlement-warnings")
  public List<SettlementTimingWarning> settlementWarnings(
      @RequestHeader("X-Admin-Token") String token) {

    validateToken(token);

    return settlementTimingWarningService.activeWarnings();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(BAD_REQUEST)
  public Map<String, Object> handleIllegalArgument(IllegalArgumentException e) {
    return Map.of("error", String.valueOf(e.getMessage()));
  }

  @ExceptionHandler(HistoricalImportFormatException.class)
  @ResponseStatus(BAD_REQUEST)
  public Map<String, Object> handleHistoricalImportFormat(HistoricalImportFormatException e) {
    return Map.of(
        "error", e.getMessage(),
        "missingHeaders", e.getMissingHeaders(),
        "requiredHeaders", e.getRequiredHeaders());
  }

  private void validateToken(String token) {
    if (adminApiToken.isBlank()) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Admin API not configured");
    }
    if (!adminApiToken.equals(token)) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
    }
  }
}
