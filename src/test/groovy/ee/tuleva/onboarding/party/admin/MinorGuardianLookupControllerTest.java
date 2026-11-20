package ee.tuleva.onboarding.party.admin;

import static ee.tuleva.onboarding.party.MinorGuardianLookupService.PAUSE_BETWEEN_QUERIES;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AmlServiceTokenValidator;
import ee.tuleva.onboarding.party.AutomaticGuardianLookups;
import ee.tuleva.onboarding.party.ChildGuardianLookup;
import ee.tuleva.onboarding.party.MinorGuardianBackfillUnavailable;
import ee.tuleva.onboarding.party.MinorGuardianLookupAlreadyRunning;
import ee.tuleva.onboarding.party.MinorGuardianLookupRun;
import ee.tuleva.onboarding.party.MinorGuardianLookupService;
import ee.tuleva.onboarding.party.NotAMinor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MinorGuardianLookupController.class)
@Import(AmlServiceTokenValidator.class)
@TestPropertySource(properties = "aml-service.api-token=aml-service-token")
@WithMockUser
class MinorGuardianLookupControllerTest {

  private static final String PATH = "/admin/aml-service/minor-guardian-lookups";
  private static final String REQUESTER = "38812121215";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MinorGuardianLookupService minorGuardianLookupService;
  @MockitoBean private AutomaticGuardianLookups automaticGuardianLookups;

  @Test
  void looksUpOneBatchAsTheRequesterTheAmlServiceNamesAndReturnsTheCounts() throws Exception {
    given(
            minorGuardianLookupService.lookUpMinorThirdPillarHolders(
                REQUESTER, 25, PAUSE_BETWEEN_QUERIES))
        .willReturn(new MinorGuardianLookupRun(24, 1, 5371));

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "batchSize": 25}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lookedUp").value(24))
        .andExpect(jsonPath("$.failed").value(1))
        .andExpect(jsonPath("$.remaining").value(5371));
  }

  @Test
  void refusesACallerWithoutTheAmlServicesToken() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .header("Authorization", "Bearer someone-else")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "batchSize": 25}
                    """))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(minorGuardianLookupService);
  }

  @Test
  void refusesARequesterCodeThatIsNotAValidPersonalCode() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38888888888", "batchSize": 25}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(minorGuardianLookupService);
  }

  @Test
  void refusesABatchOutsideOneToTheMaximum() throws Exception {
    for (String batchSize : new String[] {"0", "1001", "null"}) {
      mockMvc
          .perform(
              post(PATH)
                  .with(csrf())
                  .header("Authorization", "Bearer aml-service-token")
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"requesterPersonalCode": "38812121215", "batchSize": %s}
                      """
                          .formatted(batchSize)))
          .andExpect(status().isBadRequest());
    }

    verifyNoInteractions(minorGuardianLookupService);
  }

  @Test
  void looksUpOneChildAsTheRequesterAndSaysHowManyCustodyHoldersTheRegisterNamed()
      throws Exception {
    given(minorGuardianLookupService.lookUpChild(REQUESTER, "61506150006"))
        .willReturn(new ChildGuardianLookup(2));

    mockMvc
        .perform(
            post(PATH + "/child")
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "childPersonalCode": "61506150006"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guardians").value(2));
  }

  @Test
  void refusesAChildLookupWithoutTheTokenOrAValidChildCode() throws Exception {
    mockMvc
        .perform(
            post(PATH + "/child")
                .with(csrf())
                .header("Authorization", "Bearer someone-else")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "childPersonalCode": "61506150006"}
                    """))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post(PATH + "/child")
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "childPersonalCode": "38888888888"}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(minorGuardianLookupService);
  }

  @Test
  void answersUnprocessableForSomeoneWhoIsNotAMinor() throws Exception {
    given(minorGuardianLookupService.lookUpChild(REQUESTER, "60001019906"))
        .willThrow(new NotAMinor());

    mockMvc
        .perform(
            post(PATH + "/child")
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "childPersonalCode": "60001019906"}
                    """))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void answersConflictWhileAnotherBatchRuns() throws Exception {
    given(
            minorGuardianLookupService.lookUpMinorThirdPillarHolders(
                REQUESTER, 25, PAUSE_BETWEEN_QUERIES))
        .willThrow(new MinorGuardianLookupAlreadyRunning());

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "batchSize": 25}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void backfillsOneBatchNowAsTheDesignatedRequesterAndReturnsTheCounts() throws Exception {
    given(automaticGuardianLookups.backfillNow(10))
        .willReturn(new MinorGuardianLookupRun(10, 0, 5404));

    mockMvc
        .perform(
            post(PATH + "/backfill")
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"batchSize": 10}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lookedUp").value(10))
        .andExpect(jsonPath("$.failed").value(0))
        .andExpect(jsonPath("$.remaining").value(5404));
  }

  @Test
  void refusesABackfillWithoutTheTokenOrWithABatchOutsideOneToTheMaximum() throws Exception {
    mockMvc
        .perform(
            post(PATH + "/backfill")
                .with(csrf())
                .header("Authorization", "Bearer someone-else")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"batchSize": 10}
                    """))
        .andExpect(status().isUnauthorized());
    for (String batchSize : new String[] {"0", "1001", "null"}) {
      mockMvc
          .perform(
              post(PATH + "/backfill")
                  .with(csrf())
                  .header("Authorization", "Bearer aml-service-token")
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"batchSize": %s}
                      """
                          .formatted(batchSize)))
          .andExpect(status().isBadRequest());
    }

    verifyNoInteractions(automaticGuardianLookups);
  }

  @Test
  void answersServiceUnavailableWhenTheBackfillCannotRun() throws Exception {
    given(automaticGuardianLookups.backfillNow(10))
        .willThrow(new MinorGuardianBackfillUnavailable());

    mockMvc
        .perform(
            post(PATH + "/backfill")
                .with(csrf())
                .header("Authorization", "Bearer aml-service-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"batchSize": 10}
                    """))
        .andExpect(status().isServiceUnavailable());
  }
}
