package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.AWAITING_CONFIRMATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.EpisReportIngestionResult;
import ee.tuleva.onboarding.investment.epis.EpisReportIngestionService;
import ee.tuleva.onboarding.investment.epis.FundCycleTimeline;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycle;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaPhase;
import ee.tuleva.onboarding.investment.epis.PevaRavaStatus;
import ee.tuleva.onboarding.investment.epis.PevaRavaStatusService;
import ee.tuleva.onboarding.investment.epis.R16FundStatus;
import ee.tuleva.onboarding.investment.epis.R16Phase;
import ee.tuleva.onboarding.investment.epis.R16StatusService;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarning;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarningService;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationRequested;
import ee.tuleva.onboarding.investment.report.ReportType;
import ee.tuleva.onboarding.investment.transaction.ingest.FtConfirmationVerificationService;
import ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionRegistryController.class)
@TestPropertySource(properties = "admin.api-token=valid-token")
@WithMockUser
@RecordApplicationEvents
class TransactionRegistryControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEvents applicationEvents;

  @MockitoBean private TransactionAdminService adminService;
  @MockitoBean private FtConfirmationVerificationService ftConfirmationVerificationService;
  @MockitoBean private HistoricalRegistryImportService historicalRegistryImportService;
  @MockitoBean private EpisReportIngestionService episReportIngestionService;
  @MockitoBean private PevaRavaStatusService pevaRavaStatusService;
  @MockitoBean private R45ReportService r45ReportService;
  @MockitoBean private R16StatusService r16StatusService;
  @MockitoBean private SettlementTimingWarningService settlementTimingWarningService;

  private static final String FT_CONFIRMATION_JSON =
      """
      {
        "fund": "TUK75",
        "isin": "IE000F60HVH9",
        "tradeDate": "2026-06-08",
        "quantity": 40434,
        "grossPrice": 10.09
      }
      """;

  @Test
  void triggerMatch_publishesReconciliationEventAndReturnsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/match")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isAccepted());

    assertThat(applicationEvents.stream(RunSebPendingTransactionReconciliationRequested.class))
        .hasSize(1);
  }

  @Test
  void triggerMatch_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/match")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    assertThat(applicationEvents.stream(RunSebPendingTransactionReconciliationRequested.class))
        .isEmpty();
  }

  @Test
  void dailySummary_returnsPerFundSummary() throws Exception {
    given(adminService.dailySummary())
        .willReturn(
            new TransactionDailySummary(
                LocalDate.parse("2026-06-11"),
                List.of(
                    new TransactionDailySummary.FundSummary(
                        TUK75,
                        2,
                        new BigDecimal("15000.00"),
                        10L,
                        AWAITING_CONFIRMATION,
                        Instant.parse("2026-06-11T07:00:00Z")),
                    new TransactionDailySummary.FundSummary(
                        TUK00, 0, BigDecimal.ZERO, null, null, null))));

    mockMvc
        .perform(get("/admin/dashboard/daily-summary").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-06-11"))
        .andExpect(jsonPath("$.funds[0].fund").value("TUK75"))
        .andExpect(jsonPath("$.funds[0].unsettledOrderCount").value(2))
        .andExpect(jsonPath("$.funds[0].unsettledOrderAmount").value(15000.00))
        .andExpect(jsonPath("$.funds[0].latestBatchStatus").value("AWAITING_CONFIRMATION"))
        .andExpect(jsonPath("$.funds[1].latestBatchId").doesNotExist());
  }

  @Test
  void dailySummary_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/admin/dashboard/daily-summary").header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ftConfirmation_returnsVerificationResult() throws Exception {
    given(
            ftConfirmationVerificationService.verify(
                new FtConfirmation(
                    TUK75,
                    "IE000F60HVH9",
                    LocalDate.parse("2026-06-08"),
                    new BigDecimal("40434"),
                    new BigDecimal("10.09"))))
        .willReturn(
            Optional.of(
                new FtConfirmationResult(
                    FtVerificationStatus.OK,
                    FtVerificationStatus.PENDING_NAV,
                    Map.of("orderQuantity", "40434"))));

    mockMvc
        .perform(
            post("/admin/transaction-registry/ft-confirmation")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(FT_CONFIRMATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityStatus").value("OK"))
        .andExpect(jsonPath("$.priceStatus").value("PENDING_NAV"))
        .andExpect(jsonPath("$.details.orderQuantity").value("40434"));
  }

  @Test
  void ftConfirmation_orderNotFound_returnsNotFound() throws Exception {
    given(ftConfirmationVerificationService.verify(any())).willReturn(Optional.empty());

    mockMvc
        .perform(
            post("/admin/transaction-registry/ft-confirmation")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(FT_CONFIRMATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  void ftConfirmation_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/ft-confirmation")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType("application/json")
                .content(FT_CONFIRMATION_JSON))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(ftConfirmationVerificationService);
  }

  @Test
  void importHistory_textBody_returnsSummary() throws Exception {
    String csv = "order_id,fund_isin\nGAS-1,EE3600109435\n";
    given(historicalRegistryImportService.importCsv(csv))
        .willReturn(
            new HistoricalImportResult(
                1, 1, 1, 1, 0, List.of(), Map.of(TUK75, new BigDecimal("25025.00"))));

    mockMvc
        .perform(
            post("/admin/transaction-registry/import-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("text/plain")
                .content(csv))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rowCount").value(1))
        .andExpect(jsonPath("$.ordersCreated").value(1))
        .andExpect(jsonPath("$.executionsCreated").value(1))
        .andExpect(jsonPath("$.settlementsCreated").value(1))
        .andExpect(jsonPath("$.skippedExisting").value(0))
        .andExpect(jsonPath("$.errors").isEmpty())
        .andExpect(jsonPath("$.totalAmountByFund.TUK75").value(25025.00));
  }

  @Test
  void importHistory_multipartFile_returnsSummary() throws Exception {
    String csv = "order_id,fund_isin\nGAS-1,EE3600109435\n";
    given(historicalRegistryImportService.importCsv(csv))
        .willReturn(new HistoricalImportResult(1, 0, 0, 0, 1, List.of(), Map.of()));

    mockMvc
        .perform(
            multipart("/admin/transaction-registry/import-history")
                .file(new MockMultipartFile("file", "registry.csv", "text/csv", csv.getBytes()))
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rowCount").value(1))
        .andExpect(jsonPath("$.skippedExisting").value(1));
  }

  @Test
  void importHistory_rowErrors_returnedInSummary() throws Exception {
    given(historicalRegistryImportService.importCsv(any()))
        .willReturn(
            new HistoricalImportResult(
                2,
                0,
                0,
                0,
                0,
                List.of(new HistoricalImportResult.RowError(3, "Unknown fund: fundIsin=XX")),
                Map.of()));

    mockMvc
        .perform(
            post("/admin/transaction-registry/import-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("text/plain")
                .content("order_id,fund_isin\nGAS-1,XX\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors[0].rowNumber").value(3))
        .andExpect(jsonPath("$.errors[0].reason").value("Unknown fund: fundIsin=XX"));
  }

  @Test
  void importHistory_missingHeaders_returnsBadRequestWithRequiredHeaders() throws Exception {
    given(historicalRegistryImportService.importCsv(any()))
        .willThrow(
            new HistoricalImportFormatException(
                List.of("order_id"), List.of("order_id", "fund_isin")));

    mockMvc
        .perform(
            post("/admin/transaction-registry/import-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("text/plain")
                .content("not,a,registry,csv"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.missingHeaders[0]").value("order_id"))
        .andExpect(jsonPath("$.requiredHeaders[0]").value("order_id"))
        .andExpect(jsonPath("$.requiredHeaders[1]").value("fund_isin"));
  }

  @Test
  void importHistory_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/import-history")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType("text/plain")
                .content("order_id,fund_isin\n"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(historicalRegistryImportService);
  }

  @Test
  void importReport_textBody_returnsIngestionResult() throws Exception {
    String csv = "Tehingu liik;Summa\nSUB;100,00\n";
    given(episReportIngestionService.ingestReport(ReportType.R45, csv))
        .willReturn(
            new EpisReportIngestionResult(
                7L,
                ReportType.R45,
                LocalDate.parse("2026-06-11"),
                Map.of(TUK75, Map.of("netEur", new BigDecimal("-80000.00")))));

    mockMvc
        .perform(
            post("/admin/transaction-registry/import-report")
                .queryParam("type", "R45")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("text/plain")
                .content(csv))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportId").value(7))
        .andExpect(jsonPath("$.reportType").value("R45"))
        .andExpect(jsonPath("$.reportDate").value("2026-06-11"))
        .andExpect(jsonPath("$.fundSummaries.TUK75.netEur").value(-80000.00));
  }

  @Test
  void importReport_multipartFile_returnsIngestionResult() throws Exception {
    String csv = "Väärtpaber;Osakuid\n";
    given(episReportIngestionService.ingestReport(ReportType.R17_PEVA, csv))
        .willReturn(
            new EpisReportIngestionResult(
                8L,
                ReportType.R17_PEVA,
                LocalDate.parse("2026-06-11"),
                Map.of(TUK00, Map.of("pikUnits", new BigDecimal("1234.567890")))));

    mockMvc
        .perform(
            multipart("/admin/transaction-registry/import-report")
                .file(new MockMultipartFile("file", "r17.csv", "text/csv", csv.getBytes(UTF_8)))
                .queryParam("type", "R17_PEVA")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportId").value(8))
        .andExpect(jsonPath("$.fundSummaries.TUK00.pikUnits").value(1234.567890));
  }

  @Test
  void importReport_unknownType_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/import-report")
                .queryParam("type", "R99")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("text/plain")
                .content("csv"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(episReportIngestionService);
  }

  @Test
  void importReport_rejectedByService_returnsBadRequestWithMessage() throws Exception {
    given(episReportIngestionService.ingestReport(any(), any()))
        .willThrow(
            new IllegalArgumentException("Unsupported EPIS report type: reportType=POSITIONS"));

    mockMvc
        .perform(
            post("/admin/transaction-registry/import-report")
                .queryParam("type", "POSITIONS")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("text/plain")
                .content("csv"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void importReport_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/import-report")
                .queryParam("type", "R45")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType("text/plain")
                .content("csv"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(episReportIngestionService);
  }

  @Test
  void pevaRavaStatus_returnsPhaseCycleTimelinesAndFlows() throws Exception {
    given(pevaRavaStatusService.status())
        .willReturn(
            new PevaRavaStatus(
                PevaRavaPhase.TUK00_ACTIVE,
                new PevaRavaCycle(LocalDate.parse("2026-03-31"), LocalDate.parse("2026-05-01")),
                new FundCycleTimeline(
                    LocalDate.parse("2026-04-22"), LocalDate.parse("2026-04-27"), false, false),
                new FundCycleTimeline(
                    LocalDate.parse("2026-04-14"), LocalDate.parse("2026-04-23"), true, false),
                Map.of(
                    TUK00,
                    new PevaRavaFlows(
                        new BigDecimal("100.00"),
                        new BigDecimal("-50.00"),
                        new BigDecimal("200.00"),
                        new BigDecimal("350.00"),
                        new BigDecimal("350.00"),
                        new BigDecimal("355000"),
                        new BigDecimal("360000")))));

    mockMvc
        .perform(
            get("/admin/transaction-registry/peva-rava/status")
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("TUK00_ACTIVE"))
        .andExpect(jsonPath("$.cycle.lockDate").value("2026-03-31"))
        .andExpect(jsonPath("$.cycle.execDate").value("2026-05-01"))
        .andExpect(jsonPath("$.tuk75.dActiveDate").value("2026-04-22"))
        .andExpect(jsonPath("$.tuk00.sellByDate").value("2026-04-23"))
        .andExpect(jsonPath("$.tuk00.dActive").value(true))
        .andExpect(jsonPath("$.flows.TUK00.liquidityRequired").value(350.00));
  }

  @Test
  void pevaRavaStatus_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/admin/transaction-registry/peva-rava/status")
                .header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(pevaRavaStatusService);
  }

  @Test
  void pevaRavaCalculate_returnsRecalculatedFlows() throws Exception {
    given(pevaRavaStatusService.recalculate())
        .willReturn(
            Map.of(
                TUK75,
                new PevaRavaFlows(
                    new BigDecimal("10.00"),
                    new BigDecimal("5.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("25.00"),
                    new BigDecimal("25.00"),
                    new BigDecimal("30000"),
                    new BigDecimal("30000"))));

    mockMvc
        .perform(
            post("/admin/transaction-registry/peva-rava/calculate")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.TUK75.pikEur").value(10.00))
        .andExpect(jsonPath("$.TUK75.paymentLimit").value(30000));
  }

  @Test
  void r45Latest_returnsLatestPerFundFlows() throws Exception {
    given(r45ReportService.getLatestFlows())
        .willReturn(
            Map.of(
                TUK75,
                new R45Result(
                    new BigDecimal("150000.00"),
                    new BigDecimal("230000.00"),
                    new BigDecimal("-80000.00"))));

    mockMvc
        .perform(
            get("/admin/transaction-registry/r45/latest").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.TUK75.inflowEur").value(150000.00))
        .andExpect(jsonPath("$.TUK75.outflowEur").value(230000.00))
        .andExpect(jsonPath("$.TUK75.netEur").value(-80000.00));
  }

  @Test
  void r16Status_returnsPerFundStatuses() throws Exception {
    given(r16StatusService.status())
        .willReturn(
            List.of(
                new R16FundStatus(
                    TUK75,
                    R16Phase.ACTIVE,
                    new BigDecimal("1000"),
                    new BigDecimal("500"),
                    new BigDecimal("1200.00"),
                    LocalDate.parse("2026-06-01"),
                    LocalDate.parse("2026-06-15"),
                    LocalDate.parse("2026-06-08"),
                    false),
                new R16FundStatus(
                    TulevaFund.TUV100, R16Phase.IGNORE, null, null, null, null, null, null, true)));

    mockMvc
        .perform(
            get("/admin/transaction-registry/r16/status").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fund").value("TUK75"))
        .andExpect(jsonPath("$[0].phase").value("ACTIVE"))
        .andExpect(jsonPath("$[0].totalOutflowEur").value(1200.00))
        .andExpect(jsonPath("$[0].paymentDeadline").value("2026-06-15"))
        .andExpect(jsonPath("$[0].sellByDate").value("2026-06-08"))
        .andExpect(jsonPath("$[0].suppressedByR45").value(false))
        .andExpect(jsonPath("$[1].fund").value("TUV100"))
        .andExpect(jsonPath("$[1].phase").value("IGNORE"))
        .andExpect(jsonPath("$[1].suppressedByR45").value(true));
  }

  @Test
  void settlementWarnings_returnsActiveWarnings() throws Exception {
    given(settlementTimingWarningService.activeWarnings())
        .willReturn(
            List.of(
                new SettlementTimingWarning(
                    SettlementTimingWarning.Type.PEVA_DEADLINE_MISS,
                    TUK00,
                    LocalDate.parse("2026-05-04"),
                    LocalDate.parse("2026-05-01"),
                    "FUND sell placed today settles after PEVA/RAVA execution: fund=TUK00,"
                        + " sellSettlementDate=2026-05-04, execDate=2026-05-01")));

    mockMvc
        .perform(get("/admin/dashboard/settlement-warnings").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("PEVA_DEADLINE_MISS"))
        .andExpect(jsonPath("$[0].fund").value("TUK00"))
        .andExpect(jsonPath("$[0].sellSettlementDate").value("2026-05-04"))
        .andExpect(jsonPath("$[0].deadlineDate").value("2026-05-01"));
  }

  @Test
  void settlementWarnings_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/admin/dashboard/settlement-warnings").header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(settlementTimingWarningService);
  }

  @Test
  void ftConfirmation_withMissingField_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-registry/ft-confirmation")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(
                    """
                    {
                      "fund": "TUK75",
                      "isin": "IE000F60HVH9",
                      "tradeDate": "2026-06-08"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(ftConfirmationVerificationService);
  }
}
