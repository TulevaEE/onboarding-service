package ee.tuleva.onboarding.admin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import java.util.UUID;
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
  @MockitoBean private SavingsFundLedger savingsFundLedger;

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

  @Test
  void createAdjustment_withValidToken_returnsTransactionId() throws Exception {
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
                    {
                      "debitAccount": "INCOMING_PAYMENTS_CLEARING",
                      "creditAccount": "BANK_ADJUSTMENT",
                      "amount": 100.00,
                      "description": "Test adjustment"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value(transactionId.toString()));
  }

  @Test
  void createAdjustment_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/adjustments")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "debitAccount": "INCOMING_PAYMENTS_CLEARING",
                      "creditAccount": "BANK_ADJUSTMENT",
                      "amount": 100.00
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }
}
