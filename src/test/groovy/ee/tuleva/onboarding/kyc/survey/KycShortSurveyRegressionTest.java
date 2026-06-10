package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.kyc.TestKycCheckerConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks the assumption the TKF company-onboarding (#67) frontend-only approach depends on: a KYC
 * survey that omits the profile items (INVESTMENT_GOALS, INVESTABLE_ASSETS, SOURCE_OF_INCOME) and
 * the personal TERMS item is accepted and drives PERSON onboarding to the same COMPLETED outcome as
 * the full survey.
 *
 * <p>Note: the risk level itself is produced by the external SQL function {@code
 * kyc_ob_assess_user_risk} (deployed from the database-administration repo, not present in this
 * service's schema), so it is stubbed by {@link TestKycCheckerConfiguration} here. The profile
 * fields' irrelevance to the risk score is a property of that external function and is out of scope
 * for this service test. What this test guarantees is the service-side contract: no validation
 * requires the profile items, and the downstream onboarding-completion path is agnostic to them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@Import(TestKycCheckerConfiguration.class)
class KycShortSurveyRegressionTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private KycSurveyRepository kycSurveyRepository;
  @Autowired private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private AmlCheckRepository amlCheckRepository;

  private User user;
  private Authentication authentication;

  private static final String SHORT_SURVEY =
      """
      {
        "answers": [
          { "type": "CITIZENSHIP", "value": { "type": "COUNTRIES", "value": ["EE"] } },
          {
            "type": "ADDRESS",
            "value": {
              "type": "ADDRESS",
              "value": {
                "street": "123 Main St",
                "city": "Tallinn",
                "postalCode": "10115",
                "countryCode": "EE"
              }
            }
          },
          { "type": "EMAIL", "value": { "type": "TEXT", "value": "test@example.com" } },
          { "type": "PHONE_NUMBER", "value": { "type": "TEXT", "value": "+37255555555" } },
          { "type": "PEP_SELF_DECLARATION", "value": { "type": "OPTION", "value": "IS_NOT_PEP" } }
        ]
      }
      """;

  private static final String FULL_SURVEY =
      """
      {
        "answers": [
          { "type": "CITIZENSHIP", "value": { "type": "COUNTRIES", "value": ["EE"] } },
          {
            "type": "ADDRESS",
            "value": {
              "type": "ADDRESS",
              "value": {
                "street": "123 Main St",
                "city": "Tallinn",
                "postalCode": "10115",
                "countryCode": "EE"
              }
            }
          },
          { "type": "EMAIL", "value": { "type": "TEXT", "value": "test@example.com" } },
          { "type": "PHONE_NUMBER", "value": { "type": "TEXT", "value": "+37255555555" } },
          { "type": "PEP_SELF_DECLARATION", "value": { "type": "OPTION", "value": "IS_NOT_PEP" } },
          { "type": "INVESTMENT_GOALS", "value": { "type": "OPTION", "value": "LONG_TERM" } },
          { "type": "INVESTABLE_ASSETS", "value": { "type": "OPTION", "value": "LESS_THAN_20K" } },
          { "type": "SOURCE_OF_INCOME", "value": [ { "type": "OPTION", "value": "SALARY" } ] },
          { "type": "TERMS", "value": { "type": "OPTION", "value": "ACCEPTED" } }
        ]
      }
      """;

  private static final String IDENTITY_ONLY_SURVEY =
      """
      {
        "answers": [
          { "type": "CITIZENSHIP", "value": { "type": "COUNTRIES", "value": ["EE"] } },
          {
            "type": "ADDRESS",
            "value": {
              "type": "ADDRESS",
              "value": {
                "street": "123 Main St",
                "city": "Tallinn",
                "postalCode": "10115",
                "countryCode": "EE"
              }
            }
          },
          { "type": "EMAIL", "value": { "type": "TEXT", "value": "test@example.com" } },
          { "type": "PHONE_NUMBER", "value": { "type": "TEXT", "value": "+37255555555" } },
          { "type": "PEP_SELF_DECLARATION", "value": { "type": "OPTION", "value": "IS_NOT_PEP" } }
        ],
        "purpose": "IDENTITY_ONLY"
      }
      """;

  @BeforeEach
  void setUp() {
    user = userRepository.save(sampleUserNonMember().id(null).personalCode("48805051231").build());
    var authPerson = authenticatedPersonFromUser(user).build();
    authentication =
        new UsernamePasswordAuthenticationToken(
            authPerson, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void shortSurvey_omittingProfileItems_isAcceptedAndCompletesPersonOnboarding() throws Exception {
    submitSurvey(SHORT_SURVEY);

    var surveys = kycSurveyRepository.findAll();
    assertThat(surveys).hasSize(1);
    assertThat(surveys.getFirst().getSurvey().answers()).hasSize(5);

    assertThat(applicationEvents.stream(KycCheckPerformedEvent.class).toList())
        .singleElement()
        .satisfies(event -> assertThat(event.getPurpose()).isEqualTo(PERSONAL_ONBOARDING));
    assertThat(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .contains(COMPLETED);
    assertThatKycCheckAmlCheckIsPersisted();
  }

  @Test
  void fullSurvey_completesPersonOnboarding_identicallyToShortSurvey() throws Exception {
    submitSurvey(FULL_SURVEY);

    assertThat(applicationEvents.stream(KycCheckPerformedEvent.class).toList())
        .singleElement()
        .satisfies(event -> assertThat(event.getPurpose()).isEqualTo(PERSONAL_ONBOARDING));
    assertThat(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .contains(COMPLETED);
    assertThatKycCheckAmlCheckIsPersisted();
  }

  @Test
  void identityOnlySurvey_carriesPurposeOnEvent_andListenerCurrentlyStillCompletesPersonOnboarding()
      throws Exception {
    submitSurvey(IDENTITY_ONLY_SURVEY);

    assertThat(applicationEvents.stream(KycCheckPerformedEvent.class).toList())
        .singleElement()
        .satisfies(event -> assertThat(event.getPurpose()).isEqualTo(IDENTITY_ONLY));
    assertThat(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .contains(COMPLETED);
    assertThatKycCheckAmlCheckIsPersisted();
  }

  private void assertThatKycCheckAmlCheckIsPersisted() {
    assertThat(
            amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
                user.getPersonalCode(), aYearAgo()))
        .filteredOn(check -> check.getType() == KYC_CHECK)
        .singleElement()
        .satisfies(check -> assertThat(check.isSuccess()).isTrue());
  }

  private void submitSurvey(String body) throws Exception {
    mockMvc
        .perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isOk());
  }
}
