package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.PepStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KycIdentityController.class)
@AutoConfigureMockMvc
class KycIdentityControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private KycSurveyService kycSurveyService;

  private final AuthenticatedPerson authPerson = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void getIdentity_returnsCompleteIdentity() throws Exception {
    given(kycSurveyService.getIdentity(authPerson.getUserId()))
        .willReturn(
            new KycIdentityResponse(
                List.of("EE", "FI"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "test@example.com",
                "+37255555555",
                PepStatus.IS_NOT_PEP,
                Instant.parse("2026-06-01T10:00:00Z")));

    mvc.perform(get("/v1/kyc/identity").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citizenship[0]").value("EE"))
        .andExpect(jsonPath("$.citizenship[1]").value("FI"))
        .andExpect(jsonPath("$.address.street").value("Street 1"))
        .andExpect(jsonPath("$.address.city").value("Tallinn"))
        .andExpect(jsonPath("$.address.postalCode").value("12345"))
        .andExpect(jsonPath("$.address.countryCode").value("EE"))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.phoneNumber").value("+37255555555"))
        .andExpect(jsonPath("$.pepSelfDeclaration").value("IS_NOT_PEP"))
        .andExpect(jsonPath("$.complete").value(true))
        .andExpect(jsonPath("$.updatedAt").value("2026-06-01T10:00:00Z"));
  }

  @Test
  void getIdentity_returnsEmptyIncompleteIdentityForFreshUser() throws Exception {
    given(kycSurveyService.getIdentity(authPerson.getUserId()))
        .willReturn(new KycIdentityResponse(null, null, null, null, null, null));

    mvc.perform(get("/v1/kyc/identity").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citizenship").doesNotExist())
        .andExpect(jsonPath("$.address").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.phoneNumber").doesNotExist())
        .andExpect(jsonPath("$.pepSelfDeclaration").doesNotExist())
        .andExpect(jsonPath("$.complete").value(false))
        .andExpect(jsonPath("$.updatedAt").doesNotExist());
  }
}
