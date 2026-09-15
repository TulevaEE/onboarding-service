package ee.tuleva.onboarding.savings.fund.transfer;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Plan;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Planned;
import ee.tuleva.onboarding.savings.fund.transfer.UnitTransferVerdict.Refused;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UnitTransferController.class)
@Import({
  UnitTransferControllerTest.NoSecurity.class,
  AdminTokenValidator.class,
  UnitTransferRefusalAdvice.class
})
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
class UnitTransferControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean UnitTransferService unitTransferService;

  private static final String TRANSFERS = "/admin/savings-fund/unit-transfers";

  private static final String A_TRANSFER =
      """
      {"fromCode":"38888888888","fromType":"PERSON","toCode":"39999999999","toType":"PERSON",
       "fundUnits":"40.00000","notifiedAt":"2026-09-14","evidence":"Notice by email",
       "recipientAcquisitionCostEur":"0"}
      """;

  @Test
  void previewAnswersThePlanAndItsHash() throws Exception {
    given(unitTransferService.preview(any()))
        .willReturn(
            new Planned(
                "abc123",
                new Plan(
                    "38888888888",
                    "39999999999",
                    new BigDecimal("40.00000"),
                    new BigDecimal("60.00000"),
                    new BigDecimal("40.00000"),
                    BigDecimal.ZERO,
                    new BigDecimal("1000.00"),
                    new BigDecimal("100.00000"))));

    mockMvc
        .perform(
            post(TRANSFERS + "/preview")
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(A_TRANSFER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planHash").value("abc123"))
        .andExpect(jsonPath("$.plan.fundUnits").value(40.00000));
  }

  @Test
  void previewAnswersARefusalWithoutFailingTheRequest() throws Exception {
    given(unitTransferService.preview(any()))
        .willReturn(new Refused("The recipient has not completed savings fund onboarding"));

    mockMvc
        .perform(
            post(TRANSFERS + "/preview")
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(A_TRANSFER))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.refused").value("The recipient has not completed savings fund onboarding"));
  }

  @Test
  void aStaleConfirmIsABadRequestRatherThanAServerError() throws Exception {
    given(unitTransferService.submit(any(), eq("stale"), any()))
        .willThrow(new IllegalArgumentException("Confirm does not match the plan"));

    mockMvc
        .perform(
            post(TRANSFERS)
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(
                    "{\"transfer\":"
                        + A_TRANSFER
                        + ",\"confirm\":\"stale\",\"submittedBy\":\"operator@example.com\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void approvingYourOwnSubmissionIsAConflictRatherThanAServerError() throws Exception {
    given(unitTransferService.approve(any(), any()))
        .willThrow(
            new IllegalStateException(
                "A transfer must be approved by someone other than whoever submitted it"));

    mockMvc
        .perform(
            post(TRANSFERS + "/" + UUID.randomUUID() + "/approve")
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content("{\"approvedBy\":\"operator@example.com\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void anUnknownTransferIsNotFoundRatherThanAServerError() throws Exception {
    given(unitTransferService.approve(any(), any()))
        .willThrow(new NoSuchElementException("No such transfer"));

    mockMvc
        .perform(
            post(TRANSFERS + "/" + UUID.randomUUID() + "/approve")
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content("{\"approvedBy\":\"approver@example.com\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void submittingRecordsTheTransferAndAnswersItsState() throws Exception {
    given(unitTransferService.submit(any(), any(), any())).willReturn(anAwaitingTransfer());

    mockMvc
        .perform(
            post(TRANSFERS)
                .header("X-Admin-Token", "valid-token")
                .contentType("application/json")
                .content(
                    "{\"transfer\":"
                        + A_TRANSFER
                        + ",\"confirm\":\"abc123\",\"submittedBy\":\"operator@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("AWAITING_APPROVAL"))
        .andExpect(jsonPath("$.submittedBy").value("operator@example.com"));
  }

  @Test
  void cancellingAnswersTheTransfersState() throws Exception {
    given(unitTransferService.cancel(any())).willReturn(aCancelledTransfer());

    mockMvc
        .perform(
            post(TRANSFERS + "/" + UUID.randomUUID() + "/cancel")
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"))
        .andExpect(jsonPath("$.submittedBy").value("operator@example.com"));
  }

  @Test
  void awaitingApprovalListsWhatStillNeedsASecondPerson() throws Exception {
    given(unitTransferService.awaitingApproval()).willReturn(List.of(anAwaitingTransfer()));

    mockMvc
        .perform(get(TRANSFERS + "/awaiting-approval").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].state").value("AWAITING_APPROVAL"))
        .andExpect(jsonPath("$[0].fundUnits").value(40.00000));
  }

  @Test
  void withoutAnAdminTokenNothingIsReachable() throws Exception {
    mockMvc
        .perform(post(TRANSFERS + "/preview").contentType("application/json").content(A_TRANSFER))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(unitTransferService);
  }

  @Test
  void anApprovalWithAnUnacceptedTokenMovesNothing() throws Exception {
    mockMvc
        .perform(
            post(TRANSFERS + "/" + UUID.randomUUID() + "/approve")
                .header("X-Admin-Token", "not-the-token")
                .contentType("application/json")
                .content("{\"approvedBy\":\"approver@example.com\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(unitTransferService);
  }

  @Test
  void theOpsTokenIsNotEnoughToMoveSomeonesUnits() throws Exception {
    mockMvc
        .perform(
            post(TRANSFERS + "/" + UUID.randomUUID() + "/approve")
                .header("X-Admin-Token", "ops-token")
                .contentType("application/json")
                .content("{\"approvedBy\":\"approver@example.com\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(unitTransferService);
  }

  @Test
  void submittingWithAnUnacceptedTokenRecordsNothing() throws Exception {
    mockMvc
        .perform(
            post(TRANSFERS)
                .header("X-Admin-Token", "not-the-token")
                .contentType("application/json")
                .content(
                    "{\"transfer\":"
                        + A_TRANSFER
                        + ",\"confirm\":\"abc123\",\"submittedBy\":\"operator@example.com\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(unitTransferService);
  }

  private static UnitTransfer aCancelledTransfer() {
    UnitTransfer transfer = anAwaitingTransfer();
    transfer.cancelled(Instant.parse("2026-09-15T09:00:00Z"));
    return transfer;
  }

  private static UnitTransfer anAwaitingTransfer() {
    return UnitTransfer.builder()
        .id(UUID.randomUUID())
        .fromPartyCode("38888888888")
        .fromPartyType(PERSON)
        .toPartyCode("39999999999")
        .toPartyType(PERSON)
        .fundUnits(new BigDecimal("40.00000"))
        .notifiedAt(LocalDate.parse("2026-09-14"))
        .evidence("Notice by email")
        .giverPaidInEur(new BigDecimal("1000.00"))
        .giverUnitsOwned(new BigDecimal("100.00000"))
        .planHash("abc123")
        .state(UnitTransferState.AWAITING_APPROVAL)
        .submittedBy("operator@example.com")
        .build();
  }

  @TestConfiguration
  static class NoSecurity {

    @Bean
    org.springframework.security.web.SecurityFilterChain permitEverything(
        org.springframework.security.config.annotation.web.builders.HttpSecurity http)
        throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
          .build();
    }
  }
}
