package ee.tuleva.onboarding.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublisher;
import ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublishingResult;
import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.ChildIsNotAMinorException;
import ee.tuleva.onboarding.party.ParentChildLinkRegistrationService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.RepresentationType;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistEntry;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.UnattributedPaymentAttributionService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
  @MockitoBean private SavingsFundOnboardingService savingsFundOnboardingService;
  @MockitoBean private ParentChildLinkRegistrationService parentChildLinkRegistrationService;

  @MockitoBean
  private ee.tuleva.onboarding.investment.check.tracking.PeriodicTdAttributionService
      tdAttributionService;

  @MockitoBean
  private ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService ocfCalculationService;

  @MockitoBean private IbanWhitelistService ibanWhitelistService;
  @MockitoBean private UnattributedPaymentAttributionService unattributedPaymentAttributionService;
  @MockitoBean private Clock clock;
  @MockitoBean private InvestmentReportPublisher investmentReportPublisher;
  @MockitoBean private InvestmentReportDataService investmentReportDataService;
  @MockitoBean private InvestmentReportPdfGenerator investmentReportPdfGenerator;

  private static final String VALID_LINK_BODY =
      """
      {
        "parentCode": "38812121215",
        "childCode": "61506150006",
        "childFirstName": "Mari",
        "childLastName": "Maasikas",
        "relationshipType": "LEGAL_REPRESENTATIVE"
      }
      """;

  @Test
  void attributeUnattributedPayment_withValidOpsToken_returnsOk() throws Exception {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .amount(new BigDecimal("1000.00"))
            .status(SavingFundPayment.Status.VERIFIED)
            .build();
    given(
            unattributedPaymentAttributionService.attribute(
                paymentId, new PartyId(PartyId.Type.PERSON, "48806046007"), true))
        .willReturn(payment);

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/attribute", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("partyType", "PERSON")
                .param("partyCode", "48806046007")
                .param("returnCancelled", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.status").value("VERIFIED"))
        .andExpect(jsonPath("$.partyCode").value("48806046007"));
  }

  @Test
  void attributeUnattributedPayment_withInvalidToken_returnsUnauthorized() throws Exception {
    var paymentId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/attribute", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("partyType", "PERSON")
                .param("partyCode", "48806046007"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(unattributedPaymentAttributionService);
  }

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
  void recordBlackrockAdjustment_roundsAmountToTwoDecimalPlaces() throws Exception {
    var result =
        new BlackrockAdjustmentResult(
            TulevaFund.TUK75,
            LocalDate.of(2026, 4, 2),
            BigDecimal.ZERO,
            new BigDecimal("56980.96"),
            new BigDecimal("56980.96"),
            true);
    given(
            navFeeAccrualLedger.recordBlackrockAdjustment(
                TulevaFund.TUK75, LocalDate.of(2026, 4, 2), new BigDecimal("56980.96")))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("fundCode", "TUK75")
                .param("amount", "56980.95999999999")
                .param("date", "2026-04-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetBalance").value(56980.96));
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
  void calculateNav_withOpsToken_returnsOk() throws Exception {
    var result = sampleNavResult(LocalDate.of(2026, 2, 17));
    when(navCalculationService.calculate("TKF100", LocalDate.of(2026, 2, 17))).thenReturn(result);

    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("date", "2026-02-17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.navPerUnit").value(1.0));
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

  @Test
  void whitelistIban_withOpsToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("partyType", "PERSON")
                .param("partyCode", "39901019992")
                .param("iban", "EE471000001020145685")
                .param("comment", "verified via bank statement"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("EE471000001020145685")));

    verify(ibanWhitelistService)
        .add(
            new PartyId(PartyId.Type.PERSON, "39901019992"),
            "EE471000001020145685",
            "verified via bank statement");
  }

  @Test
  void whitelistIban_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("partyType", "PERSON")
                .param("partyCode", "39901019992")
                .param("iban", "EE471000001020145685"))
        .andExpect(status().isUnauthorized());

    verify(ibanWhitelistService, never()).add(any(), any(), any());
  }

  @Test
  void whitelistIban_withMissingPartyType_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("partyCode", "39901019992")
                .param("iban", "EE471000001020145685"))
        .andExpect(status().isBadRequest());

    verify(ibanWhitelistService, never()).add(any(), any(), any());
  }

  @Test
  void listWhitelistedIbans_withOpsToken_returnsEntriesForParty() throws Exception {
    var entry =
        new IbanWhitelistEntry(
            new PartyId(PartyId.Type.PERSON, "39901019992"),
            "EE471000001020145685",
            "verified",
            Instant.parse("2026-05-29T10:00:00Z"));
    given(ibanWhitelistService.list(new PartyId(PartyId.Type.PERSON, "39901019992")))
        .willReturn(List.of(entry));

    mockMvc
        .perform(
            get("/admin/whitelist-iban")
                .header("X-Admin-Token", "ops-token")
                .param("partyType", "PERSON")
                .param("partyCode", "39901019992"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].iban").value("EE471000001020145685"))
        .andExpect(jsonPath("$[0].comment").value("verified"));
  }

  @Test
  void whitelistIban_withInvalidIban_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("partyType", "PERSON")
                .param("partyCode", "39901019992")
                .param("iban", "not-an-iban"))
        .andExpect(status().isBadRequest());

    verify(ibanWhitelistService, never()).add(any(), any(), any());
  }

  @Test
  void listWhitelistedIbans_withNoFilter_returnsAll() throws Exception {
    given(ibanWhitelistService.list(null)).willReturn(List.of());

    mockMvc
        .perform(get("/admin/whitelist-iban").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isOk());

    verify(ibanWhitelistService).list(null);
  }

  @Test
  void listWhitelistedIbans_withOnlyPartyType_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/admin/whitelist-iban")
                .header("X-Admin-Token", "ops-token")
                .param("partyType", "PERSON"))
        .andExpect(status().isBadRequest());

    verify(ibanWhitelistService, never()).list(any());
  }

  @Test
  void listWhitelistedIbans_withOnlyPartyCode_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/admin/whitelist-iban")
                .header("X-Admin-Token", "ops-token")
                .param("partyCode", "39901019992"))
        .andExpect(status().isBadRequest());

    verify(ibanWhitelistService, never()).list(any());
  }

  @Test
  void removeWhitelistedIban_withOpsToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            delete("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("partyType", "PERSON")
                .param("partyCode", "39901019992")
                .param("iban", "EE471000001020145685"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("EE471000001020145685")));

    verify(ibanWhitelistService)
        .remove(new PartyId(PartyId.Type.PERSON, "39901019992"), "EE471000001020145685");
  }

  @Test
  void removeWhitelistedIban_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            delete("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("partyType", "PERSON")
                .param("partyCode", "39901019992")
                .param("iban", "EE471000001020145685"))
        .andExpect(status().isUnauthorized());

    verify(ibanWhitelistService, never()).remove(any(), any());
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

  @Test
  void createParentChildLink_withValidToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("61506150006")));

    verify(parentChildLinkRegistrationService)
        .register(
            "38812121215",
            "61506150006",
            "Mari",
            "Maasikas",
            RepresentationType.LEGAL_REPRESENTATIVE);
    verify(savingsFundOnboardingService).seedPersonOnboardingIfAbsent("61506150006");
  }

  @Test
  void createParentChildLink_withOpsToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isOk());

    verify(parentChildLinkRegistrationService)
        .register(
            "38812121215",
            "61506150006",
            "Mari",
            "Maasikas",
            RepresentationType.LEGAL_REPRESENTATIVE);
    verify(savingsFundOnboardingService).seedPersonOnboardingIfAbsent("61506150006");
  }

  @Test
  void createParentChildLink_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isUnauthorized());

    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any(), any());
    verify(savingsFundOnboardingService, never()).seedPersonOnboardingIfAbsent(any());
  }

  @Test
  void createParentChildLink_whenChildNotAMinor_returnsBadRequest() throws Exception {
    doThrow(new ChildIsNotAMinorException("38812121215"))
        .when(parentChildLinkRegistrationService)
        .register(any(), any(), any(), any(), any());

    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isBadRequest());

    verify(savingsFundOnboardingService, never()).seedPersonOnboardingIfAbsent(any());
  }

  @Test
  void createParentChildLink_withInvalidPersonalCode_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "parentCode": "not-a-code",
                      "childCode": "61506150006",
                      "childFirstName": "Mari",
                      "childLastName": "Maasikas",
                      "relationshipType": "LEGAL_REPRESENTATIVE"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any(), any());
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
