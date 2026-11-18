package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYB_RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;
import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.RESCREENING;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.SUBMISSION;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.OnboardingStatusChangeReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.KybScreeningTrigger;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyInputs;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Three connections are checked out at once here: the KYC transaction still holds its own while
// its after-commit callbacks run, the re-screening of one company holds a second, and the onboarded
// email holds a third. The shared test pool of two is not enough.
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class WaitingLegalEntityCompletionIntegrationTest {

  private static final String POISONED = "11111111";
  private static final String POISONED_NAME = "Kärbes OÜ";
  private static final String HEALTHY = "22222222";
  private static final String HEALTHY_NAME = "Mesila OÜ";
  private static final String UNRELATED = "33333333";
  private static final String UNRELATED_NAME = "Sipelgas OÜ";
  private static final String NEW = "44444444";
  private static final String NEW_NAME = "Kimalane OÜ";
  private static final String APPLICANT = "40404049996";
  private static final String APPLICANT_EMAIL = "mesila@example.com";
  private static final String ANOTHER_BOARD_MEMBER = "38001010001";
  private static final String TEMPLATE = "savings_fund_company_onboarded_et";

  @TestConfiguration
  static class TestConfig {
    @Bean
    PoisonedCompanyListener poisonedCompanyListener() {
      return new PoisonedCompanyListener();
    }
  }

  // Stands in for any of the KybCheckPerformedEvent listeners that join the re-screening
  // transaction: a failure in one of them marks that transaction rollback-only.
  static class PoisonedCompanyListener {
    @EventListener
    @Transactional
    public void onKybCheckPerformed(KybCheckPerformedEvent event) {
      if (POISONED.equals(event.getCompany().registryCode().value())) {
        throw new IllegalStateException("Downstream listener failed: registryCode=" + POISONED);
      }
    }
  }

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private LegalEntityScreener legalEntityScreener;
  @MockitoBean private LatestKybSurveyInputs latestKybSurveyInputs;
  @MockitoBean private EmailService emailService;

  private User applicant;

  @BeforeEach
  void setUp() {
    cleanUp();
    applicant =
        userRepository.save(
            sampleUserNonMember().id(null).personalCode(APPLICANT).email(APPLICANT_EMAIL).build());
    savingsFundOnboardingRepository.saveOnboardingStatus(
        POISONED, LEGAL_ENTITY, PENDING, KYB_CHECK);
    savingsFundOnboardingRepository.saveOnboardingStatus(HEALTHY, LEGAL_ENTITY, PENDING, KYB_CHECK);
    savingsFundOnboardingRepository.saveOnboardingStatus(
        UNRELATED, LEGAL_ENTITY, PENDING, KYB_CHECK);
    awaitKycOf(POISONED, APPLICANT);
    awaitKycOf(HEALTHY, APPLICANT);
    awaitKycOf(UNRELATED, ANOTHER_BOARD_MEMBER);

    given(latestKybSurveyInputs.findByRegistryCode(any()))
        .willReturn(
            new KybSurveyInputs(
                new PersonalCode(APPLICANT), new SelfCertification(true, true, true)));
    given(legalEntityScreener.screenLatest(any())).willAnswer(this::publishScreeningResult);
    given(emailService.newMandrillMessage(any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  void completesTheOtherWaitingCompanyWhenOneCompanyPoisonsItsOwnTransaction() {
    assertThatCode(this::performKycCheckInATransaction).doesNotThrowAnyException();

    assertThat(savingsFundOnboardingRepository.findStatus(POISONED, LEGAL_ENTITY))
        .contains(PENDING);
    assertThat(savingsFundOnboardingRepository.findStatus(HEALTHY, LEGAL_ENTITY))
        .contains(COMPLETED);
  }

  // Every waiting company would otherwise cost three registry round trips on every successful
  // personal KYC check, whether or not it waits for that person.
  @Test
  void leavesAloneTheCompanyWaitingForSomebodyElse() {
    performKycCheckInATransaction();

    verify(legalEntityScreener, never()).screenLatest(UNRELATED);
    assertThat(savingsFundOnboardingRepository.findStatus(UNRELATED, LEGAL_ENTITY))
        .contains(PENDING);
  }

  // The whole point: the person who submitted the survey keeps their own KYC check.
  @Test
  void keepsTheSubmittingPersonsKycCheckWhenAWaitingCompanyFails() {
    performKycCheckInATransaction();

    assertThat(
            amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(APPLICANT, KYC_CHECK, true))
        .hasSize(1);
  }

  @Test
  void emailsOnlyTheCompanyWhoseRescreeningCommitted() {
    performKycCheckInATransaction();

    verify(emailService)
        .newMandrillMessage(
            APPLICANT_EMAIL,
            TEMPLATE,
            Map.of(
                "fname",
                applicant.getFirstName(),
                "lname",
                applicant.getLastName(),
                "recipientName",
                HEALTHY_NAME,
                "recipientAccountId",
                companyRepository.findByRegistryCode(HEALTHY).orElseThrow().getId().toString()),
            List.of("savings_fund"));
    verify(emailService, times(1)).newMandrillMessage(any(), any(), any(), any());
  }

  @Test
  void emailsTheApplicantInEnglishWhenTheyPreferEnglish() {
    preferEnglish(APPLICANT);

    performKycCheckInATransaction();

    verify(emailService)
        .newMandrillMessage(
            APPLICANT_EMAIL,
            "savings_fund_company_onboarded_en",
            Map.of(
                "fname",
                applicant.getFirstName(),
                "lname",
                applicant.getLastName(),
                "recipientName",
                HEALTHY_NAME,
                "recipientAccountId",
                companyRepository.findByRegistryCode(HEALTHY).orElseThrow().getId().toString()),
            List.of("savings_fund"));
  }

  @Test
  void emailsTheApplicantWhenTheirNewCompanyOpensOnTheSpot() {
    screen(NEW, SUBMISSION, new KybCheck(COMPANY_ACTIVE, true, Map.of()));

    assertThat(savingsFundOnboardingRepository.findStatus(NEW, LEGAL_ENTITY)).contains(COMPLETED);
    verify(emailService)
        .newMandrillMessage(
            APPLICANT_EMAIL,
            TEMPLATE,
            Map.of(
                "fname",
                applicant.getFirstName(),
                "lname",
                applicant.getLastName(),
                "recipientName",
                NEW_NAME,
                "recipientAccountId",
                companyRepository.findByRegistryCode(NEW).orElseThrow().getId().toString()),
            List.of("savings_fund"));
  }

  @Test
  void emailsTheApplicantOnceWhenTheirNewCompanyStartsWaitingForTheOtherShareholder() {
    var waitingForShareholder =
        new KybCheck(
            RELATED_PERSONS_KYC,
            false,
            Map.of(
                "incompletePersons",
                List.of(Map.of("personalCode", ANOTHER_BOARD_MEMBER, "kycStatus", "UNKNOWN"))));

    screen(NEW, SUBMISSION, waitingForShareholder);
    screen(NEW, RESCREENING, waitingForShareholder);

    assertThat(savingsFundOnboardingRepository.findStatus(NEW, LEGAL_ENTITY)).contains(PENDING);
    verify(emailService)
        .newMandrillMessage(
            APPLICANT_EMAIL,
            "savings_fund_company_waiting_related_et",
            Map.of(
                "fname",
                applicant.getFirstName(),
                "lname",
                applicant.getLastName(),
                "recipientName",
                NEW_NAME,
                "recipientAccountId",
                companyRepository.findByRegistryCode(NEW).orElseThrow().getId().toString()),
            List.of("savings_fund"));
    verify(emailService, times(1)).newMandrillMessage(any(), any(), any(), any());
  }

  private void screen(String registryCode, KybScreeningTrigger trigger, KybCheck check) {
    eventPublisher.publishEvent(
        new KybCheckPerformedEvent(
            this,
            new CompanyDto(
                new RegistryCode(registryCode),
                nameOf(registryCode),
                List.of("62011"),
                LegalForm.OÜ),
            new PersonalCode(APPLICANT),
            List.of(),
            List.of(check),
            List.of(),
            trigger));
  }

  private void preferEnglish(String personalCode) {
    jdbcClient
        .sql(
            """
            INSERT INTO unit_owner (personal_id, language_preference, date_created, snapshot_date)
            SELECT :personalCode, 'ENG', :dateCreated, COALESCE(MAX(snapshot_date), :today)
            FROM unit_owner
            """)
        .param("personalCode", personalCode)
        .param("dateCreated", Timestamp.from(Instant.parse("2026-11-17T00:00:00Z")))
        .param("today", LocalDate.of(2026, 11, 17))
        .update();
  }

  private void performKycCheckInATransaction() {
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new KycCheckPerformedEvent(
                    this, APPLICANT, new KycCheck(LOW, Map.of()), IDENTITY_ONLY)));
  }

  private List<KybCheck> publishScreeningResult(InvocationOnMock invocation) {
    String registryCode = invocation.getArgument(0);
    eventPublisher.publishEvent(
        new KybCheckPerformedEvent(
            this,
            new CompanyDto(
                new RegistryCode(registryCode),
                nameOf(registryCode),
                List.of("62011"),
                LegalForm.OÜ),
            new PersonalCode(APPLICANT),
            List.of(),
            List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())),
            List.of(),
            RESCREENING));
    return List.of();
  }

  private String nameOf(String registryCode) {
    return switch (registryCode) {
      case POISONED -> POISONED_NAME;
      case UNRELATED -> UNRELATED_NAME;
      case NEW -> NEW_NAME;
      default -> HEALTHY_NAME;
    };
  }

  private void awaitKycOf(String registryCode, String awaitedPersonalCode) {
    var company =
        companyRepository.save(
            Company.builder().registryCode(registryCode).name(nameOf(registryCode)).build());
    amlCheckRepository.save(
        AmlCheck.builder()
            .personalCode(APPLICANT)
            .companyId(company.getId())
            .type(KYB_RELATED_PERSONS_KYC)
            .success(false)
            .metadata(
                Map.of(
                    "incompletePersons",
                    List.of(Map.of("personalCode", awaitedPersonalCode, "kycStatus", "PENDING"))))
            .build());
  }

  private void cleanUp() {
    var codes = List.of(POISONED, HEALTHY, UNRELATED, NEW);
    jdbcClient
        .sql("DELETE FROM aml_check WHERE personal_code = :personalCode")
        .param("personalCode", APPLICANT)
        .update();
    jdbcClient
        .sql("DELETE FROM unit_owner WHERE personal_id = :personalCode")
        .param("personalCode", APPLICANT)
        .update();
    jdbcClient
        .sql(
            "DELETE FROM company_party WHERE company_id IN (SELECT id FROM company WHERE registry_code IN (:codes))")
        .param("codes", codes)
        .update();
    jdbcClient
        .sql(
            "DELETE FROM company_representation_right WHERE company_id IN (SELECT id FROM company WHERE registry_code IN (:codes))")
        .param("codes", codes)
        .update();
    jdbcClient
        .sql("DELETE FROM company WHERE registry_code IN (:codes)")
        .param("codes", codes)
        .update();
    jdbcClient
        .sql("DELETE FROM savings_fund_onboarding WHERE code IN (:codes)")
        .param("codes", codes)
        .update();
    userRepository.findByPersonalCode(APPLICANT).ifPresent(userRepository::delete);
  }
}
