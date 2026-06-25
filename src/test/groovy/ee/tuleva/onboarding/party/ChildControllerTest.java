package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.LocalDate;
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

@WebMvcTest(ChildController.class)
@AutoConfigureMockMvc
class ChildControllerTest {

  private static final String CHILD = "61506150006";
  private static final String BODY =
      """
      { "childPersonalCode": "61506150006" }
      """;

  @Autowired private MockMvc mvc;

  @MockitoBean private ChildOnboardingService childOnboardingService;

  private final AuthenticatedPerson parent = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          parent, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void verifiedCustody_returnsOkWithChildIdentity() throws Exception {
    given(childOnboardingService.onboardChild(parent.getPersonalCode(), CHILD))
        .willReturn(new ChildOnboardingResult(true, "Mari", "Maasikas", LocalDate.of(2015, 6, 15)));

    mvc.perform(
            post("/v1/me/children")
                .with(csrf())
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED"))
        .andExpect(jsonPath("$.firstName").value("Mari"))
        .andExpect(jsonPath("$.lastName").value("Maasikas"))
        .andExpect(jsonPath("$.dateOfBirth").value("2015-06-15"));
  }

  @Test
  void unverifiedCustody_returnsAcceptedUnderReviewWithoutIdentity() throws Exception {
    given(childOnboardingService.onboardChild(parent.getPersonalCode(), CHILD))
        .willReturn(new ChildOnboardingResult(false, null, null, null));

    mvc.perform(
            post("/v1/me/children")
                .with(csrf())
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
        .andExpect(jsonPath("$.firstName").doesNotExist())
        .andExpect(jsonPath("$.dateOfBirth").doesNotExist());
  }
}
