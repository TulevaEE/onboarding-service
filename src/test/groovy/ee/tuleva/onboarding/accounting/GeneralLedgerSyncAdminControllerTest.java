package ee.tuleva.onboarding.accounting;

import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GeneralLedgerSyncAdminController.class)
@Import(AdminTokenValidator.class)
@ActiveProfiles("production")
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class GeneralLedgerSyncAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GeneralLedgerBackgroundSync backgroundSync;

  @Test
  void anAdminTokenStartsTheNightlyGeneralLedgerSyncInTheBackground() throws Exception {
    mockMvc
        .perform(
            post("/admin/general-ledger/sync").with(csrf()).header("X-Admin-Token", "valid-token"))
        .andExpect(status().isAccepted());

    then(backgroundSync).should().start();
  }

  @Test
  void anInvalidTokenRunsNoSync() throws Exception {
    mockMvc
        .perform(
            post("/admin/general-ledger/sync").with(csrf()).header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    then(backgroundSync).shouldHaveNoInteractions();
  }
}
