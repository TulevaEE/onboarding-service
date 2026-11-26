package ee.tuleva.onboarding.savings.fund.admin;

import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.HELD;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.SENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseApproval;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseEmailFailedException;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseRefusedException;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseService;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NavReleaseAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class NavReleaseAdminControllerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 20);
  private static final UUID CALCULATION_ID =
      UUID.fromString("6f1c2d3e-4b5a-4c6d-8e7f-901a2b3c4d5e");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NavReleaseService navReleaseService;

  @Test
  void status_withTheOpsToken_showsTheValuesToApprove() throws Exception {
    given(navReleaseService.status(TKF100, NAV_DATE)).willReturn(releaseStatus(HELD));

    mockMvc
        .perform(get("/admin/nav/TKF100/2026-11-20").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculationId").value(CALCULATION_ID.toString()))
        .andExpect(jsonPath("$.state").value("HELD"))
        .andExpect(jsonPath("$.navPerUnit").value(1.4066))
        .andExpect(jsonPath("$.aum").value(16000000.00));
  }

  @Test
  void status_withAWrongToken_isUnauthorized() throws Exception {
    mockMvc
        .perform(get("/admin/nav/TKF100/2026-11-20").header("X-Admin-Token", "wrong"))
        .andExpect(status().isUnauthorized());
    then(navReleaseService).shouldHaveNoInteractions();
  }

  @Test
  void status_withoutACalculation_isNotFound() throws Exception {
    given(navReleaseService.status(TKF100, NAV_DATE))
        .willThrow(new NoSuchElementException("No NAV calculation"));

    mockMvc
        .perform(get("/admin/nav/TKF100/2026-11-20").header("X-Admin-Token", "ops-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void release_passesTheApprovedValuesAndApproversOn() throws Exception {
    var approval =
        new NavReleaseApproval(
            new BigDecimal("1.4066"),
            new BigDecimal("16000000.00"),
            List.of("approver-one", "approver-two"));
    given(navReleaseService.release(TKF100, NAV_DATE, approval)).willReturn(releaseStatus(SENT));

    mockMvc
        .perform(release(releaseBody("[\"approver-one\", \"approver-two\"]")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SENT"));
  }

  @Test
  void release_refusedByTheService_isAConflict() throws Exception {
    given(navReleaseService.release(Mockito.eq(TKF100), Mockito.eq(NAV_DATE), Mockito.any()))
        .willThrow(new NavReleaseRefusedException("Not the latest calculation's values"));

    mockMvc
        .perform(release(releaseBody("[\"approver-one\", \"approver-two\"]")))
        .andExpect(status().isConflict());
  }

  @Test
  void release_whoseEmailToTheTrusteeFailed_isABadGateway() throws Exception {
    given(navReleaseService.release(Mockito.eq(TKF100), Mockito.eq(NAV_DATE), Mockito.any()))
        .willThrow(new NavReleaseEmailFailedException("Mandrill send failed"));

    mockMvc
        .perform(release(releaseBody("[\"approver-one\", \"approver-two\"]")))
        .andExpect(status().isBadGateway());
  }

  @Test
  void release_withoutTwoDifferentApprovers_isABadRequest() throws Exception {
    mockMvc.perform(release(releaseBody("[]"))).andExpect(status().isBadRequest());
    mockMvc.perform(release(releaseBody("[\"approver-one\"]"))).andExpect(status().isBadRequest());
    mockMvc
        .perform(release(releaseBody("[\"approver-one\", \"approver-one\"]")))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(release(releaseBody("[\"approver-one\", \" \"]")))
        .andExpect(status().isBadRequest());
    then(navReleaseService).shouldHaveNoInteractions();
  }

  @Test
  void release_withoutTheApprovedValues_isABadRequest() throws Exception {
    mockMvc
        .perform(
            release(
                """
                {"navPerUnit": 1.4066, "approvedBy": ["approver-one", "approver-two"]}
                """))
        .andExpect(status().isBadRequest());
    then(navReleaseService).shouldHaveNoInteractions();
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder release(
      String body) {
    return post("/admin/nav/TKF100/2026-11-20/release")
        .with(csrf())
        .header("X-Admin-Token", "ops-token")
        .contentType(APPLICATION_JSON)
        .content(body);
  }

  private static String releaseBody(String approvedBy) {
    return """
        {"navPerUnit": 1.4066, "aum": 16000000.00, "approvedBy": %s}
        """
        .formatted(approvedBy);
  }

  private static NavReleaseStatus releaseStatus(
      ee.tuleva.onboarding.savings.fund.nav.NavReleaseState state) {
    return new NavReleaseStatus(
        "TKF100",
        NAV_DATE,
        CALCULATION_ID,
        state,
        new BigDecimal("1.4066"),
        new BigDecimal("16000000.00"),
        null);
  }
}
