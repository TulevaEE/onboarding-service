package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static ee.tuleva.onboarding.kyc.survey.ChildRoleAwareKycIntegrationTest.Assessed.INSIDE_A_TRANSACTION;
import static ee.tuleva.onboarding.kyc.survey.ChildRoleAwareKycIntegrationTest.Assessed.OUTSIDE_ANY_TRANSACTION;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.kyc.TestKycChecker;
import ee.tuleva.onboarding.kyc.TestKycCheckerConfiguration;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.party.RepresentationType;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

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

  // Mirrors verbatim the payload onboarding-client PR #1587 sends from
  // transformChildFormDataToSurveyCommand: ADDRESS (including the frontend's optional
  // fullAddress field, which the backend AddressDetails record ignores under Jackson 3's
  // default of not failing on unknown properties), EMAIL, PHONE_NUMBER, INVESTMENT_GOALS
  // (child-only EDUCATION goal), PLANNED_CONTRIBUTION (child range), INVESTABLE_ASSETS
  // (child range), FUNDING_SOURCES, purpose PERSONAL_ONBOARDING. Citizenship and PEP are
  // intentionally absent — a child's citizenship comes from the population register and a
  // child cannot be a PEP.
  private static final String CHILD_SURVEY =
      """
      {
        "answers": [
          {
            "type": "ADDRESS",
            "value": {
              "type": "ADDRESS",
              "value": {
                "fullAddress": "123 Main St, Tallinn 10115",
                "street": "123 Main St",
                "city": "Tallinn",
                "postalCode": "10115",
                "countryCode": "EE"
              }
            }
          },
          { "type": "EMAIL", "value": { "type": "TEXT", "value": "child@example.com" } },
          { "type": "PHONE_NUMBER", "value": { "type": "TEXT", "value": "+37255500000" } },
          { "type": "INVESTMENT_GOALS", "value": { "type": "OPTION", "value": "EDUCATION" } },
          {
            "type": "PLANNED_CONTRIBUTION",
            "value": { "type": "OPTION", "value": "FROM_200_TO_600" }
          },
          { "type": "INVESTABLE_ASSETS", "value": { "type": "OPTION", "value": "UP_TO_2000" } },
          {
            "type": "FUNDING_SOURCES",
            "value": [
              { "type": "OPTION", "value": "PARENT_INCOME_AND_SAVINGS" },
              { "type": "TEXT", "value": "lottery win" }
            ]
          }
        ],
        "purpose": "PERSONAL_ONBOARDING"
      }
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private KycSurveyRepository kycSurveyRepository;
  @Autowired private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private ParentChildLinkRepository parentChildLinkRepository;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private TestKycChecker testKycChecker;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private KycSurveyService kycSurveyService;

  @MockitoBean private PepAndSanctionCheckService pepAndSanctionCheckService;

  private static final JsonMapper objectMapper = JsonMapper.builder().build();

  private User parent;
  private User child;
  private Authentication parentActingAsChild;

  @BeforeEach
  void setUp() {
    testKycChecker.reset();
    given(pepAndSanctionCheckService.match(any(), any()))
        .willReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
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
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(PARENT_CODE)
            .childPersonalCode(CHILD_CODE)
            .relationshipType(RepresentationType.LEGAL_REPRESENTATIVE)
            .validUntil(LocalDate.now().plusYears(2))
            .build());

    parentActingAsChild =
        authenticatedAs(parent, new Role(RoleType.PERSON, CHILD_CODE, "Child Person"));
  }

  @Test
  void parentActingAsChild_writesKycToChildAndCompletesChildOnboarding() throws Exception {
    submitOnboardingSurvey(parentActingAsChild);

    assertThat(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(child.getId()))
        .as("survey is stored under the child")
        .isPresent();
    assertThat(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(parent.getId()))
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
  void parentActingAsChild_screensTheChildAndTheLinkedGuardian() throws Exception {
    submitOnboardingSurvey(parentActingAsChild);

    var beforeKycEvents = applicationEvents.stream(BeforeKycCheckedEvent.class).toList();
    assertThat(beforeKycEvents).hasSize(1);
    assertThat(beforeKycEvents.getFirst().person().getPersonalCode())
        .as("the KYC check itself is aimed at the child")
        .isEqualTo(CHILD_CODE);

    var kycCheckEvents = applicationEvents.stream(KycCheckPerformedEvent.class).toList();
    assertThat(kycCheckEvents).hasSize(1);
    assertThat(kycCheckEvents.getFirst().getPersonalCode()).isEqualTo(CHILD_CODE);

    assertThat(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(CHILD_CODE, aYearAgo()))
        .extracting(AmlCheck::getType)
        .contains(KYC_CHECK, SANCTION, POLITICALLY_EXPOSED_PERSON_AUTO);
    assertThat(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PARENT_CODE, aYearAgo()))
        .as("the linked guardian is sanction/PEP screened before the child's risk assessment")
        .extracting(AmlCheck::getType)
        .contains(SANCTION, POLITICALLY_EXPOSED_PERSON_AUTO)
        .doesNotContain(KYC_CHECK);
  }

  @Test
  @Transactional(propagation = NOT_SUPPORTED)
  void parentActingAsChild_commitsTheSurveyAndBothScreeningsBeforeTheRiskAssessmentReadsThem()
      throws Exception {
    testKycChecker.givenKycCheck(child.getId(), PENDING_REVIEW);
    var committedWhenAssessed = new AtomicReference<CommittedAssessmentInputs>();
    testKycChecker.whenChecked(() -> committedWhenAssessed.set(committedAssessmentInputs()));

    submitOnboardingSurvey(parentActingAsChild);

    assertThat(committedWhenAssessed.get())
        .isEqualTo(new CommittedAssessmentInputs(OUTSIDE_ANY_TRANSACTION, 1, 2, 2));
  }

  @Test
  @Transactional(propagation = NOT_SUPPORTED)
  void parentActingAsChild_publishesOnlyTheNewerSurveysResultWhenItCommitsWhileTheOlderIsAssessed()
      throws Exception {
    testKycChecker.givenKycCheck(child.getId(), PENDING_REVIEW);
    var newerResult = new KycCheck(HIGH, Map.of("submission", "newer"));
    testKycChecker.whenChecked(
        () -> {
          testKycChecker.whenChecked(() -> {});
          testKycChecker.givenKycCheck(child.getId(), newerResult);
          kycSurveyService.submit(parentActingAsChildPerson(), onboardingSurvey());
        });

    submitOnboardingSurvey(parentActingAsChild);

    assertThat(applicationEvents.stream(KycCheckPerformedEvent.class))
        .extracting(KycCheckPerformedEvent::getKycCheck)
        .containsExactly(newerResult);
  }

  @Test
  @Transactional(propagation = NOT_SUPPORTED)
  void parentActingAsChild_keepsTheSurveyCommittedAndRecordsNoCheckWhenTheAssessmentFails() {
    testKycChecker.whenChecked(
        () -> {
          throw new IllegalStateException("aml-service is down");
        });

    assertThatThrownBy(
            () -> kycSurveyService.submit(parentActingAsChildPerson(), onboardingSurvey()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(committedRows("SELECT count(*) FROM kyc_survey WHERE user_id = :key", child.getId()))
        .isEqualTo(1);
    assertThat(
            committedRows(
                "SELECT count(*) FROM aml_check WHERE personal_code = :key AND type = 'KYC_CHECK'",
                CHILD_CODE))
        .isZero();
    assertThat(applicationEvents.stream(KycCheckPerformedEvent.class)).isEmpty();
  }

  private static final KycCheck PENDING_REVIEW = new KycCheck(MEDIUM, Map.of());

  enum Assessed {
    INSIDE_A_TRANSACTION,
    OUTSIDE_ANY_TRANSACTION
  }

  private record CommittedAssessmentInputs(
      Assessed assessed, long childSurveys, long childScreenings, long guardianScreenings) {}

  private CommittedAssessmentInputs committedAssessmentInputs() {
    var assessed = isActualTransactionActive() ? INSIDE_A_TRANSACTION : OUTSIDE_ANY_TRANSACTION;
    var inItsOwnTransaction = new TransactionTemplate(transactionManager);
    inItsOwnTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    return inItsOwnTransaction.execute(
        status ->
            new CommittedAssessmentInputs(
                assessed,
                committedRows(
                    "SELECT count(*) FROM kyc_survey WHERE user_id = :key", child.getId()),
                screenings(CHILD_CODE),
                screenings(PARENT_CODE)));
  }

  private long screenings(String personalCode) {
    return committedRows(
        "SELECT count(*) FROM aml_check WHERE personal_code = :key"
            + " AND type IN ('SANCTION', 'POLITICALLY_EXPOSED_PERSON_AUTO')",
        personalCode);
  }

  private long committedRows(String countQuery, Object key) {
    return jdbcClient.sql(countQuery).param("key", key).query(Long.class).single();
  }

  private AuthenticatedPerson parentActingAsChildPerson() {
    return authenticatedPersonFromUser(parent)
        .role(new Role(RoleType.PERSON, CHILD_CODE, "Child Person"))
        .build();
  }

  private static KycSurveyResponse onboardingSurvey() {
    return objectMapper.readValue(ONBOARDING_SURVEY, KycSurveyResponse.class);
  }

  @AfterEach
  void deleteWhatATestWithoutATransactionCommitted() {
    testKycChecker.reset();
    if (TestTransaction.isActive()) {
      return;
    }
    var personalCodes = List.of(PARENT_CODE, CHILD_CODE);
    jdbcClient
        .sql("DELETE FROM aml_check WHERE personal_code IN (:codes)")
        .param("codes", personalCodes)
        .update();
    jdbcClient
        .sql("DELETE FROM savings_fund_onboarding WHERE code IN (:codes)")
        .param("codes", personalCodes)
        .update();
    jdbcClient
        .sql("DELETE FROM event_log WHERE principal IN (:codes)")
        .param("codes", personalCodes)
        .update();
    jdbcClient
        .sql("DELETE FROM kyc_survey WHERE user_id IN (:userIds)")
        .param("userIds", List.of(parent.getId(), child.getId()))
        .update();
    jdbcClient
        .sql("DELETE FROM parent_child_link WHERE child_personal_code = :code")
        .param("code", CHILD_CODE)
        .update();
    userRepository.deleteAll(List.of(parent, child));
  }

  @Test
  void identityRead_returnsChildIdentityWhenActingAsChild() throws Exception {
    submitOnboardingSurvey(parentActingAsChild);

    mockMvc
        .perform(get("/v1/kyc/identity").with(authentication(parentActingAsChild)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citizenship[0]").value("EE"))
        .andExpect(jsonPath("$.citizenship[1]").value("FI"))
        .andExpect(jsonPath("$.email").value("survey@example.com"))
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

    var storedSurvey =
        kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(child.getId());
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
