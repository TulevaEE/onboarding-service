package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ThirdPillarPaymentReminderController.class)
@AutoConfigureMockMvc
class ThirdPillarPaymentReminderControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private EmailPersistenceService emailPersistenceService;

  @Test
  void cancelsPendingPaymentRemindersForTheAuthenticatedUser() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));
    given(emailPersistenceService.cancel(person, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE))
        .willReturn(List.of());

    mvc.perform(
            post("/v1/third-pillar-payment-reminders/cancellations")
                .with(csrf())
                .with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cancelledCount", is(0)));
  }
}
