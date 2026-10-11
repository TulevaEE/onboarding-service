package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.MEMBER;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DESIGN;
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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(HackathonIdeaController.class)
class HackathonIdeaControllerTest {

  private static final Instant DEADLINE = Instant.parse("2026-09-30T20:59:59Z");
  private static final Instant SUBMITTED = Instant.parse("2026-09-15T10:00:00Z");

  private static final String VALID_IDEA =
      """
      {
        "challenge": "FAIR_LENDING",
        "problem": "Laenu vahetamine on tülikas",
        "solution": "Fondiosaku tagatisel krediidiliin",
        "neededSkills": ["DESIGN", "DATA_AND_AI"]
      }
      """;

  @Autowired private MockMvc mvc;

  @MockitoBean private HackathonIdeaService hackathonIdeaService;

  private final AuthenticatedPerson authenticatedPerson =
      sampleAuthenticatedPersonAndMember().build();

  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authenticatedPerson, null, List.of(new SimpleGrantedAuthority(MEMBER)));

  private final HackathonIdeaDto submittedIdea =
      new HackathonIdeaDto(
          7L,
          FAIR_LENDING,
          "Laenu vahetamine on tülikas",
          "Fondiosaku tagatisel krediidiliin",
          null,
          List.of(DESIGN, DATA_AND_AI),
          null,
          SUBMITTED);

  @Test
  void getIdeas_returnsMySubmittedIdeas() throws Exception {
    given(hackathonIdeaService.getIdeas(authenticatedPerson))
        .willReturn(new HackathonIdeasDto(true, DEADLINE, true, List.of(submittedIdea)));

    mvc.perform(get("/v1/hackathon-ideas").with(authentication(authentication)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.open", is(true)))
        .andExpect(jsonPath("$.registered", is(true)))
        .andExpect(jsonPath("$.ideas[0].id", is(7)))
        .andExpect(jsonPath("$.ideas[0].challenge", is("FAIR_LENDING")))
        .andExpect(jsonPath("$.ideas[0].neededSkills", contains("DESIGN", "DATA_AND_AI")));
  }

  @Test
  void submit_savesTheIdeaAndReturnsIt() throws Exception {
    var request =
        new HackathonIdeaRequest(
            FAIR_LENDING,
            "Laenu vahetamine on tülikas",
            "Fondiosaku tagatisel krediidiliin",
            null,
            List.of(DESIGN, DATA_AND_AI),
            null);
    given(hackathonIdeaService.submit(eq(authenticatedPerson), eq(request)))
        .willReturn(submittedIdea);

    postIdea(VALID_IDEA)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(7)))
        .andExpect(jsonPath("$.solution", is("Fondiosaku tagatisel krediidiliin")));

    verify(hackathonIdeaService).submit(authenticatedPerson, request);
  }

  @Test
  void submit_afterTheDeadline_returnsBadRequestWithTheClosedErrorCode() throws Exception {
    given(hackathonIdeaService.submit(eq(authenticatedPerson), any()))
        .willThrow(new HackathonIdeaSubmissionClosedException(DEADLINE, DEADLINE.plusSeconds(1)));

    postIdea(VALID_IDEA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("HACKATHON_IDEA_SUBMISSION_CLOSED")));
  }

  @Test
  void submit_withoutARegistration_returnsBadRequestWithTheRegistrationRequiredErrorCode()
      throws Exception {
    given(hackathonIdeaService.submit(eq(authenticatedPerson), any()))
        .willThrow(
            new HackathonRegistrationRequiredException(authenticatedPerson.getUserIdOrThrow()));

    postIdea(VALID_IDEA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("HACKATHON_REGISTRATION_REQUIRED")));
  }

  @Test
  void submit_withoutAProblem_returnsBadRequest() throws Exception {
    postIdea(
            """
            {
              "challenge": "FAIR_LENDING",
              "solution": "Fondiosaku tagatisel krediidiliin",
              "neededSkills": []
            }
            """)
        .andExpect(status().isBadRequest());

    verifyNoInteractions(hackathonIdeaService);
  }

  @Test
  void submit_withABlankSolution_returnsBadRequest() throws Exception {
    postIdea(
            """
            {
              "challenge": "FAIR_LENDING",
              "problem": "Laenu vahetamine on tülikas",
              "solution": "   ",
              "neededSkills": []
            }
            """)
        .andExpect(status().isBadRequest());

    verifyNoInteractions(hackathonIdeaService);
  }

  @Test
  void submit_withUnknownChallenge_returnsBadRequest() throws Exception {
    postIdea(
            """
            {
              "challenge": "WORLD_PEACE",
              "problem": "Laenu vahetamine on tülikas",
              "solution": "Fondiosaku tagatisel krediidiliin",
              "neededSkills": []
            }
            """)
        .andExpect(status().isBadRequest());

    verifyNoInteractions(hackathonIdeaService);
  }

  private ResultActions postIdea(String body) throws Exception {
    return mvc.perform(
        post("/v1/hackathon-ideas")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(authentication(authentication))
            .with(csrf()));
  }
}
