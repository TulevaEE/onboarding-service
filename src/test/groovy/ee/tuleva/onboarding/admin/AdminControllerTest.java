package ee.tuleva.onboarding.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@TestPropertySource(properties = "admin.api-token=valid-token")
@WithMockUser
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationEventPublisher eventPublisher;

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
}
