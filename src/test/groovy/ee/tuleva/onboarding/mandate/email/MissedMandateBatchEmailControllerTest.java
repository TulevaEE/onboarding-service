package ee.tuleva.onboarding.mandate.email;

import static org.hamcrest.Matchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MissedMandateBatchEmailController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = "admin.api-token=valid-token")
@WithMockUser
class MissedMandateBatchEmailControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MissedMandateBatchEmails missedMandateBatchEmails;

  @Test
  void listsTheBatchesMissingTheirEmailWithinTheGivenDays() throws Exception {
    given(missedMandateBatchEmails.find(7)).willReturn(List.of(2718L, 2719L));

    mockMvc
        .perform(
            get("/admin/missed-mandate-batch-emails")
                .header("X-Admin-Token", "valid-token")
                .param("days", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", contains(2718, 2719)));
  }

  @Test
  void resendsTheMissedEmailsAndReportsWhichWereSentAndWhichFailed() throws Exception {
    given(missedMandateBatchEmails.resend(7))
        .willReturn(new MissedEmailResend(List.of(2718L), List.of(2719L)));

    mockMvc
        .perform(
            post("/admin/missed-mandate-batch-emails/resend")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("days", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sentBatchIds", contains(2718)))
        .andExpect(jsonPath("$.failedBatchIds", contains(2719)));
  }

  @Test
  void refusesToResendWithoutAValidAdminToken() throws Exception {
    mockMvc
        .perform(
            post("/admin/missed-mandate-batch-emails/resend")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("days", "7"))
        .andExpect(status().isUnauthorized());

    then(missedMandateBatchEmails).shouldHaveNoInteractions();
  }
}
