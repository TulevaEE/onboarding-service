package ee.tuleva.onboarding.rejoin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.analytics.SecondPillarLeavers;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.RejoinPermission;
import ee.tuleva.onboarding.epis.RejoinPermissionSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(RejoinController.class)
@AutoConfigureMockMvc
@Import(RejoinPermissions.class)
class RejoinControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private RejoinPermissionSource rejoinPermissionSource;
  @MockitoBean private SecondPillarLeavers secondPillarLeavers;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

  @Test
  void tellsTheLoggedInPersonWhetherTheyMayRejoinTheSecondPillar() throws Exception {
    given(rejoinPermissionSource.getRejoinPermission()).willReturn(new RejoinPermission(true));

    askForPermission()
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"permitted": true, "decision": "PERMITTED"}
                    """));
  }

  @Test
  void tellsAPersonEpisRefusesThatTheyMayNotRejoinWithoutNamingAnEndWithoutAnApplication()
      throws Exception {
    given(rejoinPermissionSource.getRejoinPermission()).willReturn(new RejoinPermission(false));

    askForPermission()
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"permitted": false, "decision": "REFUSED_BY_PENSIONIKESKUS"}
                    """));
  }

  @Test
  void refusesAPersonWhoseContributionsEndedWithoutAnApplicationEvenThoughEpisWouldOfferTheForm()
      throws Exception {
    given(secondPillarLeavers.contributionsCannotRestart(person.getRepresentedPersonalCode()))
        .willReturn(true);

    askForPermission()
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"permitted": false, "decision": "CONTRIBUTIONS_CANNOT_RESTART"}
                    """));
    verifyNoInteractions(rejoinPermissionSource);
  }

  private ResultActions askForPermission() throws Exception {
    return mvc.perform(
        get("/v1/rejoin-permission")
            .with(csrf())
            .with(
                authentication(
                    new UsernamePasswordAuthenticationToken(
                        person, null, List.of(new SimpleGrantedAuthority(USER))))));
  }
}
