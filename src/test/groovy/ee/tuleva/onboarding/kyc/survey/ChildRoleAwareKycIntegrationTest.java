package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.kyc.TestKycChecker;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@Import(TestKycCheckerConfiguration.class)
class ChildRoleAwareKycIntegrationTest {

  private static final String PARENT_CODE = "47508120049";
  private static final String CHILD_CODE = "61603140009";

  private static final String ONBOARDING_SURVEY =
      """
      {
        "answers": [
          { "type": "CITIZENSHIP", "value": { "type": "COUNTRIES", "value": ["EE", "FI"] } },
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
          { "type": "EMAIL", "value": { "type": "TEXT", "value": "survey@example.com" } },
          { "type": "PEP_SELF_DECLARATION", "value": { "type": "OPTION", "value": "IS_NOT_PEP" } },
          { "type": "INVESTMENT_GOALS", "value": { "type": "OPTION", "value": "LONG_TERM" } },
          { "type": "INVESTABLE_ASSETS", "value": { "type": "OPTION", "value": "LESS_THAN_20K" } },
          { "type": "SOURCE_OF_INCOME", "value": [ { "type": "OPTION", "value": "SALARY" } ] },
          { "type": "TERMS", "value": { "type": "OPTION", "value": "ACCEPTED" } }
        ]
      }
      """;

  // The child survey deliberately carries NO CITIZENSHIP and NO PEP_SELF_DECLARATION (a child's
  // citizenship comes from the population register, not the survey), and adds the child-specific
  // FUNDING_SOURCES + PLANNED_CONTRIBUTION items and the EDUCATION investment goal.
  private static final String CHILD_SURVEY =
      """
      {
        "answers": [
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
          { "type": "EMAIL", "value": { "type": "TEXT", "value": "child@example.com" } },
          { "type": "INVESTMENT_GOALS", "value": { "type": "OPTION", "value": "EDUCATION" } },
          { "type": "INVESTABLE_ASSETS", "value": { "type": "OPTION", "value": "LESS_THAN_20K" } },
          { "type": "SOURCE_OF_INCOME", "value": [ { "type": "OPTION", "value": "SALARY" } ] },
          {
            "type": "FUNDING_SOURCES",
            "value": [
              { "type": "OPTION", "value": "PARENT_INCOME_AND_SAVINGS" },
              { "type": "TEXT", "value": "lottery win" }
            ]
          },
          {
            "type": "PLANNED_CONTRIBUTION",
            "value": { "type": "OPTION", "value": "FROM_50_TO_100" }
          },
          { "type": "TERMS", "value": { "type": "OPTION", "value": "ACCEPTED" } }
        ]
      }
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private KycSurveyRepository kycSurveyRepository;
  @Autowired private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private TestKycChecker testKycChecker;

  private User parent;
  private User child;
  private Authentication parentActingAsChild;

  @BeforeEach
  void setUp() {
    testKycChecker.reset();
    parent =
        userRepository.save(
            sampleUserNonMember()
                .id(null)
                .personalCode(PARENT_CODE)
                .firstName("Parent")
                .lastName("Person")
                .email("parent@example.com")
                .build());
    child =
        userRepository.save(
            sampleUserNonMember()
                .id(null)
                .personalCode(CHILD_CODE)
                .firstName("Child")
                .lastName("Person")
                .email("child@example.com")
                .phoneNumber("+37255500000")
                .build());

    parentActingAsChild =
        authenticatedAs(parent, new Role(RoleType.PERSON, CHILD_CODE, "Child Person"));
  }

  @Test
  void parentActingAsChild_writesKycToChildAndCompletesChildOnboarding() throws Exception {
    submitOnboardingSurvey(parentActingAsChild);

    assertThat(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(child.getId()))
        .as("survey is stored under the child")
        .isPresent();
    assertThat(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(parent.getId()))
        .as("survey is NOT stored under the parent")
        .isEmpty();

    assertThat(savingsFundOnboardingRepository.findStatus(child.getPersonalCode(), PERSON))
        .as("child onboarding completes")
        .contains(COMPLETED);
    assertThat(savingsFundOnboardingRepository.findStatus(parent.getPersonalCode(), PERSON))
        .as("parent onboarding is untouched")
        .isEmpty();
  }

  @Test
  void parentActingAsChild_screensTheChildNotTheParent() throws Exception {
    submitOnboardingSurvey(parentActingAsChild);

    var beforeKycEvents = applicationEvents.stream(BeforeKycCheckedEvent.class).toList();
    assertThat(beforeKycEvents).hasSize(1);
    assertThat(beforeKycEvents.getFirst().person().getPersonalCode())
        .as("sanction/PEP screening is aimed at the child")
        .isEqualTo(CHILD_CODE);

    var kycCheckEvents = applicationEvents.stream(KycCheckPerformedEvent.class).toList();
    assertThat(kycCheckEvents).hasSize(1);
    assertThat(kycCheckEvents.getFirst().getPersonalCode()).isEqualTo(CHILD_CODE);

    assertThat(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(CHILD_CODE, aYearAgo()))
        .extracting(AmlCheck::getType)
        .contains(KYC_CHECK);
    assertThat(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PARENT_CODE, aYearAgo()))
        .as("no AML checks are written against the parent")
        .isEmpty();
  }

  @Test
  void identityRead_returnsChildIdentityWhenActingAsChild() throws Exception {
    submitOnboardingSurvey(parentActingAsChild);

    mockMvc
        .perform(get("/v1/kyc/identity").with(authentication(parentActingAsChild)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citizenship[0]").value("EE"))
        .andExpect(jsonPath("$.citizenship[1]").value("FI"))
        .andExpect(jsonPath("$.email").value(child.getEmail()))
        .andExpect(jsonPath("$.complete").value(true));

    var parentSelf =
        authenticatedAs(parent, new Role(RoleType.PERSON, PARENT_CODE, "Parent Person"));
    mockMvc
        .perform(get("/v1/kyc/identity").with(authentication(parentSelf)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(false));
  }

  @Test
  void parentActingAsChild_childSurveyWithoutCitizenshipOrPep_isAcceptedAndCompletes()
      throws Exception {
    submitSurvey(parentActingAsChild, CHILD_SURVEY);

    var storedSurvey = kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(child.getId());
    assertThat(storedSurvey)
        .as("child survey without CITIZENSHIP/PEP is accepted and stored under the child")
        .isPresent();
    assertThat(storedSurvey.get().getSurvey().citizenship())
        .as("child survey carries no citizenship")
        .isEmpty();
    assertThat(storedSurvey.get().getSurvey().pepSelfDeclaration())
        .as("child survey carries no PEP self-declaration")
        .isEmpty();

    assertThat(savingsFundOnboardingRepository.findStatus(child.getPersonalCode(), PERSON))
        .as("child onboarding completes")
        .contains(COMPLETED);
  }

  private void submitOnboardingSurvey(Authentication as) throws Exception {
    submitSurvey(as, ONBOARDING_SURVEY);
  }

  private void submitSurvey(Authentication as, String body) throws Exception {
    mockMvc
        .perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .with(authentication(as)))
        .andExpect(status().isOk());
  }

  private static Authentication authenticatedAs(User user, Role role) {
    var principal = authenticatedPersonFromUser(user).role(role).build();
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority(USER)));
  }
}
