package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecurringPaymentConfirmationController.class)
@Import(SecurityConfiguration.class)
class RecurringPaymentConfirmationControllerTest {

  private static final String CONFIRMATIONS = "/v1/me/third-pillar/recurring-payment-confirmations";

  @Autowired private MockMvc mvc;

  @MockitoBean private ThirdPillarStandingOrders standingOrders;

  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  private static Authentication authenticated(AuthenticatedPerson person) {
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void recordsTheConfirmationOfThePersonTheUserActsForWithoutContent() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonNonMember().build();

    mvc.perform(
            post(CONFIRMATIONS)
                .contentType(APPLICATION_JSON)
                .content("{}")
                .with(authentication(authenticated(person)))
                .with(csrf()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(standingOrders).recordConfirmation(person);
  }

  @Test
  void rejectsAnUnauthenticatedConfirmation() throws Exception {
    mvc.perform(post(CONFIRMATIONS).contentType(APPLICATION_JSON).content("{}").with(csrf()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(standingOrders);
  }
}
