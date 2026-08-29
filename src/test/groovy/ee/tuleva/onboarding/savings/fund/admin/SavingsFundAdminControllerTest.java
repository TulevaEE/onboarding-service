package ee.tuleva.onboarding.savings.fund.admin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistEntry;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.UnattributedPaymentAttributionService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionReviewService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavingsFundAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class SavingsFundAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NavCalculationService navCalculationService;
  @MockitoBean private NavPublisher navPublisher;
  @MockitoBean private RedemptionBatchJob redemptionBatchJob;
  @MockitoBean private RedemptionReviewService redemptionReviewService;
  @MockitoBean private IbanWhitelistService ibanWhitelistService;
  @MockitoBean private UnattributedPaymentAttributionService unattributedPaymentAttributionService;
  @MockitoBean private Clock clock;

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
  void attributeUnattributedPayment_withOpsTokenDifferingInLastCharacter_returnsUnauthorized()
      throws Exception {
    var paymentId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/admin/savings-fund/payments/{paymentId}/attribute", paymentId)
                .with(csrf())
                .header("X-Admin-Token", "ops-tokeX")
                .param("partyType", "PERSON")
                .param("partyCode", "48806046007"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(unattributedPaymentAttributionService);
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
  void approveRedemptionReview_withOpsToken_delegatesToService() throws Exception {
    var requestId = UUID.fromString("2db696b5-00ee-4937-87b4-8192c675e4b5");

    mockMvc
        .perform(
            post("/admin/redemptions/{id}/approve-review", requestId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("approvedBy", "AML Specialist")
                .param("reason", "reviewed, source of funds clear"))
        .andExpect(status().isOk());

    verify(redemptionReviewService)
        .approve(requestId, "AML Specialist", "reviewed, source of funds clear");
  }

  @Test
  void approveRedemptionReview_withBlankReason_returnsBadRequest() throws Exception {
    var requestId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/admin/redemptions/{id}/approve-review", requestId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("approvedBy", "AML Specialist")
                .param("reason", " "))
        .andExpect(status().isBadRequest());

    verify(redemptionReviewService, never()).approve(any(), any(), any());
  }

  @Test
  void approveRedemptionReview_withBlankApprover_returnsBadRequest() throws Exception {
    var requestId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/admin/redemptions/{id}/approve-review", requestId)
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("approvedBy", " ")
                .param("reason", "reviewed"))
        .andExpect(status().isBadRequest());

    verify(redemptionReviewService, never()).approve(any(), any(), any());
  }

  @Test
  void approveRedemptionReview_withInvalidToken_returnsUnauthorized() throws Exception {
    var requestId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/admin/redemptions/{id}/approve-review", requestId)
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("approvedBy", "AML Specialist")
                .param("reason", "reviewed"))
        .andExpect(status().isUnauthorized());

    verify(redemptionReviewService, never()).approve(any(), any(), any());
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
