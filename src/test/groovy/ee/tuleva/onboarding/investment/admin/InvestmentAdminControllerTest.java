package ee.tuleva.onboarding.investment.admin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.position.FundPositionImportJob;
import ee.tuleva.onboarding.investment.position.FundPositionLedgerService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.ReportImportJob;
import ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublisher;
import ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublishingResult;
import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestmentAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class InvestmentAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FundPositionImportJob fundPositionImportJob;
  @MockitoBean private FundPositionLedgerService fundPositionLedgerService;
  @MockitoBean private FundPositionRepository fundPositionRepository;
  @MockitoBean private ReportImportJob reportImportJob;
  @MockitoBean private FeeAccrualRepository feeAccrualRepository;
  @MockitoBean private NavFeeAccrualLedger navFeeAccrualLedger;
  @MockitoBean private NavCalculationService navCalculationService;
  @MockitoBean private InvestmentReportPublisher investmentReportPublisher;
  @MockitoBean private InvestmentReportDataService investmentReportDataService;
  @MockitoBean private InvestmentReportPdfGenerator investmentReportPdfGenerator;

  @MockitoBean
  private ee.tuleva.onboarding.investment.check.tracking.PeriodicTdAttributionService
      tdAttributionService;

  @MockitoBean
  private ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService ocfCalculationService;

  @MockitoBean private Clock clock;

  @Test
  void backfillFees_callsServiceWithFundAndDateRange() throws Exception {
    mockMvc
        .perform(
            post("/admin/backfill-fees")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TKF100")
                .param("from", "2026-02-03")
                .param("to", "2026-02-03"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("TKF100")))
        .andExpect(content().string(containsString("2026-02-03")));

    verify(navCalculationService)
        .backfillFees(TulevaFund.TKF100, LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 3));
  }

  @Test
  void backfillFees_rejectsInvalidToken() throws Exception {
    mockMvc
        .perform(
            post("/admin/backfill-fees")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TKF100")
                .param("from", "2026-02-03")
                .param("to", "2026-02-16"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rerecordPositions_rerecordsPositionsAndBackfillsFees() throws Exception {
    when(fundPositionRepository.findLatestNavDateByFund(TulevaFund.TUK75))
        .thenReturn(java.util.Optional.of(LocalDate.of(2026, 3, 13)));

    mockMvc
        .perform(
            post("/admin/rerecord-positions")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75")
                .param("fromDate", "2026-03-01"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("TUK75")))
        .andExpect(content().string(containsString("2026-03-01")));

    verify(fundPositionLedgerService).rerecordPositions(TulevaFund.TUK75, LocalDate.of(2026, 3, 1));
    verify(navCalculationService)
        .backfillFees(TulevaFund.TUK75, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 13));
  }

  @Test
  void rerecordPositions_rejectsInvalidToken() throws Exception {
    mockMvc
        .perform(
            post("/admin/rerecord-positions")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TUK75")
                .param("fromDate", "2026-03-01"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void backfillPositions_callsRecordPositionsForEachDate() throws Exception {
    var dates =
        List.of(
            LocalDate.of(2026, 2, 3),
            LocalDate.of(2026, 2, 4),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 2));
    when(fundPositionRepository.findDistinctNavDatesByFund(TulevaFund.TKF100)).thenReturn(dates);

    mockMvc
        .perform(
            post("/admin/backfill-positions")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TKF100")
                .param("from", "2026-03-01")
                .param("to", "2026-03-02"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("TKF100")))
        .andExpect(content().string(containsString("2")));

    verify(fundPositionLedgerService)
        .recordPositionsToLedger(TulevaFund.TKF100, LocalDate.of(2026, 3, 1));
    verify(fundPositionLedgerService)
        .recordPositionsToLedger(TulevaFund.TKF100, LocalDate.of(2026, 3, 2));
    verifyNoMoreInteractions(fundPositionLedgerService);
  }

  @Test
  void reimportPositions_delegatesToJobsForProvider() throws Exception {
    mockMvc
        .perform(
            post("/admin/reimport-positions")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("provider", "SEB")
                .param("date", "2026-03-10"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("SEB")))
        .andExpect(content().string(containsString("2026-03-10")));

    verify(reportImportJob)
        .forceImportForProviderAndDate(
            ee.tuleva.onboarding.investment.report.ReportProvider.SEB, LocalDate.of(2026, 3, 10));
    verify(fundPositionImportJob)
        .importForProviderAndDate(
            ee.tuleva.onboarding.investment.report.ReportProvider.SEB, LocalDate.of(2026, 3, 10));
  }

  @Test
  void reimportPositions_rejectsInvalidToken() throws Exception {
    mockMvc
        .perform(
            post("/admin/reimport-positions")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("provider", "SEB")
                .param("date", "2026-03-10"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void backfillPositions_rejectsInvalidToken() throws Exception {
    mockMvc
        .perform(
            post("/admin/backfill-positions")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TKF100"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void publishInvestmentReports_withValidTokenAndParams_publishesForGivenMonth() throws Exception {
    var fixedInstant = Instant.parse("2026-04-15T10:00:00Z");
    given(clock.instant()).willReturn(fixedInstant);
    given(clock.getZone()).willReturn(ZoneId.of("UTC"));

    var expectedResult =
        new InvestmentReportPublishingResult(
            Map.of("TUK75", "https://tuleva.ee/test.pdf"), true, List.of());
    given(investmentReportPublisher.publish(YearMonth.of(2026, 3))).willReturn(expectedResult);

    mockMvc
        .perform(
            post("/admin/publish-investment-reports")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("month", "3")
                .param("year", "2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailSent").value(true));

    verify(investmentReportPublisher).publish(YearMonth.of(2026, 3));
  }

  @Test
  void publishInvestmentReports_withoutParams_defaultsToPreviousMonth() throws Exception {
    var fixedInstant = Instant.parse("2026-04-15T10:00:00Z");
    given(clock.instant()).willReturn(fixedInstant);
    given(clock.getZone()).willReturn(ZoneId.of("UTC"));

    var expectedResult = new InvestmentReportPublishingResult(Map.of(), false, List.of());
    given(investmentReportPublisher.publish(YearMonth.of(2026, 3))).willReturn(expectedResult);

    mockMvc
        .perform(
            post("/admin/publish-investment-reports")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk());

    verify(investmentReportPublisher).publish(YearMonth.of(2026, 3));
  }

  @Test
  void publishInvestmentReports_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/publish-investment-reports")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    verify(investmentReportPublisher, never()).publish(any());
  }

  @Test
  void publishInvestmentReports_withInvalidMonth_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/publish-investment-reports")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("month", "13")
                .param("year", "2026"))
        .andExpect(status().isBadRequest());

    verify(investmentReportPublisher, never()).publish(any());
  }

  @Test
  void publishInvestmentReports_withFutureMonth_returnsBadRequest() throws Exception {
    given(clock.instant()).willReturn(Instant.parse("2026-04-15T10:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("UTC"));

    mockMvc
        .perform(
            post("/admin/publish-investment-reports")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("month", "12")
                .param("year", "2026"))
        .andExpect(status().isBadRequest());

    verify(investmentReportPublisher, never()).publish(any());
  }

  @Test
  void previewInvestmentReport_returnsGeneratedPdf() throws Exception {
    given(clock.instant()).willReturn(Instant.parse("2026-04-15T10:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("UTC"));
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};
    given(investmentReportDataService.getReportData(TulevaFund.TUK75, YearMonth.of(2026, 3)))
        .willReturn(sampleReportContext());
    given(investmentReportPdfGenerator.generatePdf(any())).willReturn(pdfBytes);

    mockMvc
        .perform(
            get("/admin/preview-investment-report")
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75")
                .param("month", "3")
                .param("year", "2026"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"));
  }

  @Test
  void previewInvestmentReport_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/admin/preview-investment-report")
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TUK75")
                .param("month", "3")
                .param("year", "2026"))
        .andExpect(status().isUnauthorized());
  }

  private static InvestmentReportContext sampleReportContext() {
    return new InvestmentReportContext(
        "Tuleva Maailma Aktsiate Pensionifond",
        "31.03.2026",
        List.of(),
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        BigDecimal.ZERO,
        null,
        new BigDecimal("0.998"),
        new BigDecimal("10000000"));
  }

  @Test
  void calculateOcf_forSingleFund_returnsOk() throws Exception {
    var snapshot =
        new ee.tuleva.onboarding.investment.fees.ocf.OcfSnapshot(
            1L,
            "TUK75",
            LocalDate.of(2026, 4, 1),
            new BigDecimal("0.00340000"),
            new BigDecimal("0.00100000"),
            new BigDecimal("0.00070000"),
            new BigDecimal("0.00020000"),
            new BigDecimal("0.00530000"));
    given(ocfCalculationService.calculateOcf(TulevaFund.TUK75, java.time.YearMonth.of(2026, 4)))
        .willReturn(snapshot);

    mockMvc
        .perform(
            post("/admin/ocf")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75")
                .param("month", "2026-04"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("TUK75")));
  }

  @Test
  void calculateOcf_forAllFunds_returnsOk() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("month", "2026-04"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("all funds")));

    verify(ocfCalculationService).calculateForAllFunds(java.time.YearMonth.of(2026, 4));
  }

  @Test
  void calculateOcf_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/admin/ocf").with(csrf()).header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void backfillOcf_returnsOk() throws Exception {
    mockMvc
        .perform(
            post("/admin/ocf-backfill")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("monthsBack", "3"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("3 months")));
  }

  @Test
  void computeTdAttribution_forSingleFund_returnsOk() throws Exception {
    var result =
        ee.tuleva.onboarding.investment.check.tracking.TdAttributionResult.builder()
            .fund(TulevaFund.TUK75)
            .tdGeometric(new BigDecimal("0.0005"))
            .build();
    given(
            tdAttributionService.computeAttribution(
                eq(TulevaFund.TUK75),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30)),
                eq(ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY)))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/td-attribution")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75")
                .param("from", "2026-04-01")
                .param("to", "2026-04-30"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("TUK75")));
  }

  @Test
  void computeTdAttribution_forAllFunds_returnsOk() throws Exception {
    mockMvc
        .perform(
            post("/admin/td-attribution")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-04-01")
                .param("to", "2026-04-30"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("all funds")));

    verify(tdAttributionService)
        .computeForAllFunds(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30),
            ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY);
  }

  @Test
  void computeTdAttribution_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/td-attribution")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("from", "2026-04-01")
                .param("to", "2026-04-30"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void backfillTdAttribution_returnsOk() throws Exception {
    mockMvc
        .perform(
            post("/admin/td-attribution-backfill")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("monthsBack", "3"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("3 months")));

    verify(tdAttributionService).backfillMonths(eq(3), any());
  }
}
