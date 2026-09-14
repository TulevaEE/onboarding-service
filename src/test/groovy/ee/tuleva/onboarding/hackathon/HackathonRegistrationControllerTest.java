package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.MEMBER;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.WEALTH_AND_INHERITANCE;
import static ee.tuleva.onboarding.hackathon.HackathonParticipation.LOOKING_FOR_TEAM;
import static ee.tuleva.onboarding.hackathon.HackathonRole.PARTICIPANT;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.SOFTWARE_DEVELOPMENT;
import static ee.tuleva.onboarding.hackathon.HackathonTshirtColor.NONE;
import static ee.tuleva.onboarding.hackathon.HackathonTshirtColor.WHITE;
import static ee.tuleva.onboarding.hackathon.HackathonTshirtSize.M;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HackathonRegistrationController.class)
class HackathonRegistrationControllerTest {

  private static final Instant DEADLINE = Instant.parse("2026-10-05T20:59:59Z");

  @Autowired private MockMvc mvc;

  @MockitoBean private HackathonRegistrationService hackathonRegistrationService;

  private final AuthenticatedPerson authenticatedPerson =
      sampleAuthenticatedPersonAndMember().build();

  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authenticatedPerson, null, List.of(new SimpleGrantedAuthority(MEMBER)));

  @Test
  void getRegistration_returnsPrefilledFormForANewRegistrant() throws Exception {
    given(hackathonRegistrationService.getRegistration(authenticatedPerson))
        .willReturn(
            new HackathonRegistrationDto(
                false,
                true,
                DEADLINE,
                "participant@example.com",
                "+37255555555",
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                false));

    mvc.perform(get("/v1/hackathon-registration").with(authentication(authentication)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registered", is(false)))
        .andExpect(jsonPath("$.open", is(true)))
        .andExpect(jsonPath("$.email", is("participant@example.com")))
        .andExpect(jsonPath("$.phoneNumber", is("+37255555555")))
        .andExpect(jsonPath("$.role").doesNotExist())
        .andExpect(jsonPath("$.termsAccepted", is(false)));
  }

  @Test
  void register_savesTheRegistrationAndReturnsIt() throws Exception {
    var request =
        new HackathonRegistrationRequest(
            "participant@example.com",
            "+37255555555",
            PARTICIPANT,
            List.of(SOFTWARE_DEVELOPMENT, DATA_AND_AI),
            List.of(FAIR_LENDING, WEALTH_AND_INHERITANCE),
            LOOKING_FOR_TEAM,
            "Fondiosaku tagatisel krediidiliin",
            "https://linkedin.com/in/example",
            WHITE,
            M,
            true);

    given(hackathonRegistrationService.register(eq(authenticatedPerson), eq(request)))
        .willReturn(
            new HackathonRegistrationDto(
                true,
                true,
                DEADLINE,
                request.email(),
                request.phoneNumber(),
                request.role(),
                request.skills(),
                request.challenges(),
                request.participation(),
                request.idea(),
                request.linkedinUrl(),
                WHITE,
                M,
                true));

    mvc.perform(
            post("/v1/hackathon-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "participant@example.com",
                      "phoneNumber": "+37255555555",
                      "role": "PARTICIPANT",
                      "skills": ["SOFTWARE_DEVELOPMENT", "DATA_AND_AI"],
                      "challenges": ["FAIR_LENDING", "WEALTH_AND_INHERITANCE"],
                      "participation": "LOOKING_FOR_TEAM",
                      "idea": "Fondiosaku tagatisel krediidiliin",
                      "linkedinUrl": "https://linkedin.com/in/example",
                      "tshirtColor": "WHITE",
                      "tshirtSize": "M",
                      "termsAccepted": true
                    }
                    """)
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registered", is(true)))
        .andExpect(jsonPath("$.role", is("PARTICIPANT")))
        .andExpect(jsonPath("$.skills", contains("SOFTWARE_DEVELOPMENT", "DATA_AND_AI")))
        .andExpect(jsonPath("$.participation", is("LOOKING_FOR_TEAM")))
        .andExpect(jsonPath("$.tshirtColor", is("WHITE")))
        .andExpect(jsonPath("$.tshirtSize", is("M")))
        .andExpect(jsonPath("$.termsAccepted", is(true)));

    verify(hackathonRegistrationService).register(authenticatedPerson, request);
  }

  @Test
  void register_withoutOptionalFieldsAndWithoutAShirt_savesTheRegistration() throws Exception {
    var request =
        new HackathonRegistrationRequest(
            "participant@example.com",
            null,
            PARTICIPANT,
            List.of(),
            List.of(),
            LOOKING_FOR_TEAM,
            null,
            null,
            NONE,
            null,
            true);

    given(hackathonRegistrationService.register(eq(authenticatedPerson), eq(request)))
        .willReturn(
            new HackathonRegistrationDto(
                true,
                true,
                DEADLINE,
                request.email(),
                null,
                request.role(),
                List.of(),
                List.of(),
                request.participation(),
                null,
                null,
                NONE,
                null,
                true));

    mvc.perform(
            post("/v1/hackathon-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "participant@example.com",
                      "role": "PARTICIPANT",
                      "skills": [],
                      "challenges": [],
                      "participation": "LOOKING_FOR_TEAM",
                      "tshirtColor": "NONE",
                      "termsAccepted": true
                    }
                    """)
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registered", is(true)));

    verify(hackathonRegistrationService).register(authenticatedPerson, request);
  }

  @Test
  void register_afterTheDeadline_returnsBadRequestWithTheClosedErrorCode() throws Exception {
    given(hackathonRegistrationService.register(eq(authenticatedPerson), any()))
        .willThrow(new HackathonRegistrationClosedException(DEADLINE, DEADLINE.plusSeconds(1)));

    mvc.perform(
            post("/v1/hackathon-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "participant@example.com",
                      "role": "PARTICIPANT",
                      "skills": [],
                      "challenges": [],
                      "participation": "LOOKING_FOR_TEAM",
                      "tshirtColor": "NONE",
                      "termsAccepted": true
                    }
                    """)
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("HACKATHON_REGISTRATION_CLOSED")));
  }

  @Test
  void register_withInvalidEmail_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "not-an-email",
          "role": "PARTICIPANT",
          "skills": [],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "NONE",
          "termsAccepted": true
        }
        """);
  }

  @Test
  void register_withoutEmail_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "role": "PARTICIPANT",
          "skills": [],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "NONE",
          "termsAccepted": true
        }
        """);
  }

  @Test
  void register_withoutRole_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "participant@example.com",
          "skills": [],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "NONE",
          "termsAccepted": true
        }
        """);
  }

  @Test
  void register_withANullSkillInTheList_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "participant@example.com",
          "role": "PARTICIPANT",
          "skills": [null],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "NONE",
          "termsAccepted": true
        }
        """);
  }

  @Test
  void register_withUnknownSkill_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "participant@example.com",
          "role": "PARTICIPANT",
          "skills": ["UNDERWATER_BASKET_WEAVING"],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "NONE",
          "termsAccepted": true
        }
        """);
  }

  @Test
  void register_withoutAcceptingTheTerms_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "participant@example.com",
          "role": "PARTICIPANT",
          "skills": [],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "NONE",
          "termsAccepted": false
        }
        """);
  }

  @Test
  void register_withoutATshirtChoice_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "participant@example.com",
          "role": "PARTICIPANT",
          "skills": [],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "termsAccepted": true
        }
        """);
  }

  @Test
  void register_withAShirtButNoSize_returnsBadRequest() throws Exception {
    postExpectingBadRequest(
        """
        {
          "email": "participant@example.com",
          "role": "PARTICIPANT",
          "skills": [],
          "challenges": [],
          "participation": "LOOKING_FOR_TEAM",
          "tshirtColor": "WHITE",
          "termsAccepted": true
        }
        """);
  }

  private void postExpectingBadRequest(String body) throws Exception {
    mvc.perform(
            post("/v1/hackathon-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(hackathonRegistrationService);
  }
}
