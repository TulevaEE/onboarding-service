package ee.tuleva.onboarding.kyb.admin;

import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.kyb.KybCheckOverrideService;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KybAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class KybAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KybCheckOverrideService kybCheckOverrideService;
  @MockitoBean private Clock clock;

  @Test
  void overrideKybCheck_withAdminToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("registryCode", "12934765")
                .param("checkType", "SINGLE_BOARD_MEMBER_OWNERSHIP")
                .param("reason", "single shareholder, two spousal beneficial owners"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("12934765")));

    verify(kybCheckOverrideService)
        .forceSuccess(
            "12934765",
            SINGLE_BOARD_MEMBER_OWNERSHIP,
            "single shareholder, two spousal beneficial owners");
  }

  @Test
  void overrideKybCheck_withOpsToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("registryCode", "12934765")
                .param("checkType", "SINGLE_BOARD_MEMBER_OWNERSHIP")
                .param("reason", "single shareholder, two spousal beneficial owners"))
        .andExpect(status().isOk());

    verify(kybCheckOverrideService)
        .forceSuccess(
            "12934765",
            SINGLE_BOARD_MEMBER_OWNERSHIP,
            "single shareholder, two spousal beneficial owners");
  }

  @Test
  void overrideKybCheck_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("registryCode", "12934765")
                .param("checkType", "SINGLE_BOARD_MEMBER_OWNERSHIP")
                .param("reason", "single shareholder, two spousal beneficial owners"))
        .andExpect(status().isUnauthorized());

    verify(kybCheckOverrideService, never()).forceSuccess(any(), any(), any());
    verify(kybCheckOverrideService, never()).forceSuccess(any(), any(), any(), any());
  }

  @Test
  void overrideKybCheck_withNonForceableCheck_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("registryCode", "12934765")
                .param("checkType", "COMPANY_SANCTION")
                .param("reason", "single shareholder, two spousal beneficial owners"))
        .andExpect(status().isBadRequest());

    verify(kybCheckOverrideService, never()).forceSuccess(any(), any(), any());
    verify(kybCheckOverrideService, never()).forceSuccess(any(), any(), any(), any());
  }

  @Test
  void overrideKybCheck_withBlankReason_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("registryCode", "12934765")
                .param("checkType", "SINGLE_BOARD_MEMBER_OWNERSHIP")
                .param("reason", " "))
        .andExpect(status().isBadRequest());

    verify(kybCheckOverrideService, never()).forceSuccess(any(), any(), any());
    verify(kybCheckOverrideService, never()).forceSuccess(any(), any(), any(), any());
  }

  @Test
  void overrideKybCheck_withExpiresAt_delegatesWithExpiry() throws Exception {
    given(clock.instant()).willReturn(Instant.parse("2026-07-22T10:00:00Z"));

    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("registryCode", "12934765")
                .param("checkType", "SINGLE_BOARD_MEMBER_OWNERSHIP")
                .param("reason", "single shareholder, two spousal beneficial owners")
                .param("expiresAt", "2027-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("12934765")));

    verify(kybCheckOverrideService)
        .forceSuccess(
            "12934765",
            SINGLE_BOARD_MEMBER_OWNERSHIP,
            "single shareholder, two spousal beneficial owners",
            Instant.parse("2027-01-01T00:00:00Z"));
  }

  @Test
  void overrideKybCheck_withPastExpiresAt_returnsBadRequest() throws Exception {
    given(clock.instant()).willReturn(Instant.parse("2026-07-22T10:00:00Z"));

    mockMvc
        .perform(
            post("/admin/override-kyb-check")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("registryCode", "12934765")
                .param("checkType", "SINGLE_BOARD_MEMBER_OWNERSHIP")
                .param("reason", "single shareholder, two spousal beneficial owners")
                .param("expiresAt", "2020-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(kybCheckOverrideService);
  }
}
