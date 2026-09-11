package ee.tuleva.onboarding.admin;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.UnattributedPaymentAttributionService;
import ee.tuleva.onboarding.savings.fund.admin.SavingsFundAdminController;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionReviewService;
import java.time.Clock;
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
@TestPropertySource(properties = "admin.api-token=it-token")
@WithMockUser
class WhitelistIbanEndpointIntegrationTest {

  private static final PartyId PARTY = new PartyId(PERSON, "39901019992");
  private static final String IBAN = "EE471000001020145685";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private IbanWhitelistService ibanWhitelistService;

  // Unrelated SavingsFundAdminController dependencies, required only to satisfy the constructor.
  @MockitoBean private NavCalculationService navCalculationService;
  @MockitoBean private NavPublisher navPublisher;
  @MockitoBean private RedemptionBatchJob redemptionBatchJob;
  @MockitoBean private RedemptionReviewService redemptionReviewService;
  @MockitoBean private UnattributedPaymentAttributionService unattributedPaymentAttributionService;
  @MockitoBean private Clock clock;

  @Test
  void whitelistIban_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", "PERSON")
                .param("partyCode", PARTY.code())
                .param("iban", IBAN)
                .param("comment", "verified via bank statement"))
        .andExpect(status().isOk());

    verify(ibanWhitelistService).add(PARTY, IBAN, "verified via bank statement");
  }

  @Test
  void whitelistIban_acceptsAndForwardsAMessyFormattedIban() throws Exception {
    String messyIban = "ee47 1000 0010 2014 5685";

    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", "PERSON")
                .param("partyCode", PARTY.code())
                .param("iban", messyIban))
        .andExpect(status().isOk());

    verify(ibanWhitelistService).add(PARTY, messyIban, null);
  }

  @Test
  void removeIban_delegatesToService() throws Exception {
    mockMvc
        .perform(
            delete("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", "PERSON")
                .param("partyCode", PARTY.code())
                .param("iban", IBAN))
        .andExpect(status().isOk());

    verify(ibanWhitelistService).remove(PARTY, IBAN);
  }
}
