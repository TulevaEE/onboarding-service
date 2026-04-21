package ee.tuleva.onboarding.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceSynchronizer;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.position.FundPositionImportJob;
import ee.tuleva.onboarding.investment.position.FundPositionLedgerService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.ReportImportJob;
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationEventPublisher eventPublisher;
  @MockitoBean private SavingsFundLedger savingsFundLedger;
  @MockitoBean private NavFeeAccrualLedger navFeeAccrualLedger;
  @MockitoBean private FeeAccrualRepository feeAccrualRepository;
  @MockitoBean private NavCalculationService navCalculationService;
  @MockitoBean private NavPublisher navPublisher;
  @MockitoBean private FundBalanceSynchronizer fundBalanceSynchronizer;
  @MockitoBean private FundPositionLedgerService fundPositionLedgerService;
  @MockitoBean private FundPositionRepository fundPositionRepository;
  @MockitoBean private ReportImportJob reportImportJob;
  @MockitoBean private FundPositionImportJob fundPositionImportJob;
  @MockitoBean private RedemptionBatchJob redemptionBatchJob;
  @MockitoBean private Clock clock;

  @Test
  void fetchSebHistory_withValidToken_returnsOk() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2026-01-01")))
        .andExpect(content().string(containsString("2026-01-31")));
  }

  @Test
  void fetchSebHistory_withAccountParam_fetchesOnlyThatAccount() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .param("account", "FUND_INVESTMENT_EUR"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("FUND_INVESTMENT_EUR")))
        .andExpect(content().string(not(containsString("DEPOSIT_EUR"))))
        .andExpect(content().string(not(containsString("WITHDRAWAL_EUR"))));
  }

  @Test
  void fetchSebHistory_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void fetchSebHistory_withMissingToken_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .param("from", "2026-01-01")
                .param("to", "2026-01-31"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAdjustments_withValidToken_returnsTransactionIds() throws Exception {
    var transactionId = UUID.randomUUID();
    var transaction = LedgerTransaction.builder().id(transactionId).build();
    when(savingsFundLedger.recordAdjustment(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(transaction);

    mockMvc
        .perform(
            post("/admin/adjustments")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    [
                      {
                        "debitAccount": "CASH_RESERVED",
                        "debitPartyCode": "39107050268",
                        "debitPartyType": "PERSON",
                        "creditAccount": "CASH",
                        "creditPartyCode": "39107050268",
                        "creditPartyType": "PERSON",
                        "amount": 1.01,
                        "description": "Reverse duplicate"
                      },
                      {
                        "debitAccount": "CASH_RESERVED",
                        "debitPartyCode": "48709090311",
                        "debitPartyType": "PERSON",
                        "creditAccount": "CASH",
                        "creditPartyCode": "48709090311",
                        "creditPartyType": "PERSON",
                        "amount": 500.00,
                        "description": "Reverse duplicate"
                      }
                    ]
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));

    verify(savingsFundLedger, times(2))
        .recordAdjustment(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void createAdjustments_withDynamicAccountTypes_returnsTransactionIds() throws Exception {
    var transactionId = UUID.randomUUID();
    var transaction = LedgerTransaction.builder().id(transactionId).build();
    when(savingsFundLedger.recordAdjustment(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(transaction);

    mockMvc
        .perform(
            post("/admin/adjustments")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    [
                      {
                        "debitAccount": "TRADE_UNIT_SETTLEMENT:TKF100:LU1291102447",
                        "creditAccount": "SECURITIES_CUSTODY:TKF100:LU1291102447",
                        "amount": 11704,
                        "description": "Trade unit backfill"
                      }
                    ]
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));
  }

  @Test
  void createAdjustments_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/adjustments")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void calculateNav_withPublishTrue_calculatesAndPublishes() throws Exception {
    var result = sampleNavResult(LocalDate.of(2026, 2, 17));
    when(navCalculationService.calculate("TKF100", LocalDate.of(2026, 2, 17))).thenReturn(result);

    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("date", "2026-02-17")
                .param("publish", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.navPerUnit").value(1.0))
        .andExpect(jsonPath("$.aum").value(1000000));

    verify(navPublisher).publish(result);
  }

  @Test
  void calculateNav_defaultsToNotPublishing() throws Exception {
    var result = sampleNavResult(LocalDate.of(2026, 2, 17));
    when(navCalculationService.calculate("TKF100", LocalDate.of(2026, 2, 17))).thenReturn(result);

    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("date", "2026-02-17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.navPerUnit").value(1.0));

    verify(navPublisher, never()).publish(any());
  }

  @Test
  void calculateNav_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("date", "2026-02-17"))
        .andExpect(status().isUnauthorized());
  }

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
  void backfillUnitCounts_callsSynchronizerWithDateRange() throws Exception {
    mockMvc
        .perform(
            post("/admin/backfill-unit-counts")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-03-01")
                .param("to", "2026-03-06"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2026-03-01")))
        .andExpect(content().string(containsString("2026-03-06")));

    verify(fundBalanceSynchronizer)
        .backfillUnitCounts(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 6));
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
  void recordBlackrockAdjustment_withOpsToken_returnsOk() throws Exception {
    var result =
        new BlackrockAdjustmentResult(
            TulevaFund.TUK75,
            LocalDate.of(2026, 4, 2),
            BigDecimal.ZERO,
            new BigDecimal("38531.70"),
            new BigDecimal("38531.70"),
            true);
    given(
            navFeeAccrualLedger.recordBlackrockAdjustment(
                TulevaFund.TUK75, LocalDate.of(2026, 4, 2), new BigDecimal("38531.70")))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("fundCode", "TUK75")
                .param("amount", "38531.70")
                .param("date", "2026-04-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fund").value("TUK75"))
        .andExpect(jsonPath("$.delta").value(38531.70))
        .andExpect(jsonPath("$.transactionCreated").value(true));
  }

  @Test
  void recordBlackrockAdjustment_withAdminToken_returnsOk() throws Exception {
    var result =
        new BlackrockAdjustmentResult(
            TulevaFund.TUK75,
            LocalDate.of(2026, 4, 2),
            BigDecimal.ZERO,
            new BigDecimal("38531.70"),
            new BigDecimal("38531.70"),
            true);
    given(
            navFeeAccrualLedger.recordBlackrockAdjustment(
                TulevaFund.TUK75, LocalDate.of(2026, 4, 2), new BigDecimal("38531.70")))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75")
                .param("amount", "38531.70")
                .param("date", "2026-04-02"))
        .andExpect(status().isOk());
  }

  @Test
  void recordBlackrockAdjustment_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TUK75")
                .param("amount", "38531.70")
                .param("date", "2026-04-02"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void calculateNav_withOpsToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("date", "2026-02-17"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void retryRedemptionPayout_withValidToken_invokesBatchJobAndReturnsOk() throws Exception {
    var requestId = UUID.fromString("2db696b5-00ee-4937-87b4-8192c675e4b5");

    mockMvc
        .perform(
            post("/admin/redemptions/{id}/retry-payout", requestId)
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk());

    verify(redemptionBatchJob).retryFailedPayout(requestId);
  }

  @Test
  void retryRedemptionPayout_withInvalidToken_returnsUnauthorized() throws Exception {
    var requestId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/admin/redemptions/{id}/retry-payout", requestId)
                .with(csrf())
                .header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    verify(redemptionBatchJob, never()).retryFailedPayout(any());
  }

  @Test
  void retryRedemptionPayout_withMissingToken_returnsBadRequest() throws Exception {
    var requestId = UUID.randomUUID();

    mockMvc
        .perform(post("/admin/redemptions/{id}/retry-payout", requestId).with(csrf()))
        .andExpect(status().isBadRequest());

    verify(redemptionBatchJob, never()).retryFailedPayout(any());
  }

  private NavCalculationResult sampleNavResult(LocalDate date) {
    return NavCalculationResult.builder()
        .fund(TulevaFund.fromCode("TKF100"))
        .calculationDate(date)
        .securitiesValue(new BigDecimal("800000"))
        .cashPosition(new BigDecimal("200000"))
        .receivables(BigDecimal.ZERO)
        .pendingSubscriptions(BigDecimal.ZERO)
        .pendingRedemptions(BigDecimal.ZERO)
        .managementFeeAccrual(BigDecimal.ZERO)
        .depotFeeAccrual(BigDecimal.ZERO)
        .payables(BigDecimal.ZERO)
        .blackrockAdjustment(BigDecimal.ZERO)
        .aum(new BigDecimal("1000000"))
        .unitsOutstanding(new BigDecimal("1000000"))
        .navPerUnit(BigDecimal.ONE)
        .positionReportDate(date)
        .priceDate(date)
        .calculatedAt(Instant.parse("2026-02-17T15:30:00Z"))
        .securitiesDetail(List.of())
        .build();
  }
}
