package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.nudge.ExperimentArm.TREATMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@WebMvcTest(PaymentRateRedirectController.class)
@Import(SecurityConfiguration.class)
class PaymentRateRedirectControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PaymentRateRedirectService paymentRateRedirectService;

  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  private static Authentication authenticated(AuthenticatedPerson person) {
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void anAssignedTreatmentPersonIsSentToTheRatePageWithTheirArmAndSeason() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();
    given(paymentRateRedirectService.assign(person))
        .willReturn(PaymentRateRedirect.to(TREATMENT, 2026));

    mvc.perform(
            post("/v1/me/payment-rate-redirect")
                .with(authentication(authenticated(person)))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"redirect\":true,\"arm\":\"TREATMENT\",\"seasonYear\":2026}"));
  }

  @Test
  void anAnswerOfNoCarriesNeitherArmNorSeason() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();
    given(paymentRateRedirectService.assign(person)).willReturn(PaymentRateRedirect.no());

    mvc.perform(
            post("/v1/me/payment-rate-redirect")
                .with(authentication(authenticated(person)))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redirect").value(false))
        .andExpect(jsonPath("$.arm").doesNotExist())
        .andExpect(jsonPath("$.seasonYear").doesNotExist());
  }

  @Test
  void aDismissalIsAcceptedWithoutContent() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

    mvc.perform(
            post("/v1/me/payment-rate-redirect/dismissal")
                .with(authentication(authenticated(person)))
                .with(csrf()))
        .andExpect(status().isNoContent());

    verify(paymentRateRedirectService).dismiss(person);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(post("/v1/me/payment-rate-redirect").with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/v1/me/payment-rate-redirect/dismissal").with(csrf()))
        .andExpect(status().isForbidden());
  }
}
