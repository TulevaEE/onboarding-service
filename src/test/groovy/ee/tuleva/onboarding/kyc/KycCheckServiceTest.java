package ee.tuleva.onboarding.kyc;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.MEDIUM;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.kyc.survey.KycSurvey;
import ee.tuleva.onboarding.kyc.survey.KycSurveyRepository;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponse;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Email;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.EmailValue;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class KycCheckServiceTest {

  private static final KycCheck KYC_CHECK = new KycCheck(MEDIUM, Map.of("level", 2));

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private KycChecker kycChecker;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private KycSurveyRepository kycSurveyRepository;

  private final User subject = User.builder().id(7L).personalCode("38888888888").build();
  private final KycSurvey submitted = survey(Instant.parse("2026-11-04T10:00:00Z"));

  private KycCheckService kycCheckService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(new SimpleTransactionStatus());
    kycCheckService =
        new KycCheckService(
            eventPublisher,
            kycChecker,
            kycSurveyRepository,
            new TransactionTemplate(transactionManager));
  }

  @Test
  void beforeCheck_announcesTheCheckWithTheSubjectsCountries() {
    kycCheckService.beforeCheck(subject, Countries.of("EE", "FI"));

    verify(eventPublisher)
        .publishEvent(new BeforeKycCheckedEvent(subject, Countries.of("EE", "FI")));
  }

  @Test
  void check_assessesOutsideATransactionThenPublishesTheResultInsideOne() {
    given(kycChecker.check(subject)).willReturn(KYC_CHECK);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(7L))
        .willReturn(Optional.of(submitted));

    kycCheckService.check(subject, submitted);

    var inOrder = inOrder(kycChecker, transactionManager, kycSurveyRepository, eventPublisher);
    inOrder.verify(kycChecker).check(subject);
    inOrder.verify(transactionManager).getTransaction(any());
    inOrder.verify(kycSurveyRepository).findFirstByUserIdOrderByCreatedTimeDescIdDesc(7L);
    inOrder
        .verify(eventPublisher)
        .publishEvent(
            argThat(
                (KycCheckPerformedEvent performed) ->
                    performed.getPersonalCode().equals("38888888888")
                        && performed.getKycCheck().equals(KYC_CHECK)
                        && performed.getPurpose() == PERSONAL_ONBOARDING));
    inOrder.verify(transactionManager).commit(any());
  }

  @Test
  void check_publishesNothingWhenANewerSurveyCommittedWhileAmlServiceAssessed() {
    given(kycChecker.check(subject)).willReturn(KYC_CHECK);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(7L))
        .willReturn(Optional.of(survey(Instant.parse("2026-11-04T10:00:01Z"))));

    kycCheckService.check(subject, submitted);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void check_publishesNothingWhenTheSubmittedSurveyIsNoLongerFound() {
    given(kycChecker.check(subject)).willReturn(KYC_CHECK);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(7L))
        .willReturn(Optional.empty());

    kycCheckService.check(subject, submitted);

    verifyNoInteractions(eventPublisher);
  }

  @Test
  void check_publishesNothingWhenTheAssessmentFails() {
    willThrow(new IllegalStateException("aml-service is down")).given(kycChecker).check(subject);

    assertThatThrownBy(() -> kycCheckService.check(subject, submitted))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(eventPublisher, transactionManager, kycSurveyRepository);
  }

  private static KycSurvey survey(Instant createdTime) {
    return KycSurvey.builder()
        .id(UUID.randomUUID())
        .userId(7L)
        .survey(
            new KycSurveyResponse(
                List.of(new Email(new EmailValue("TEXT", "someone@example.com"))),
                PERSONAL_ONBOARDING))
        .createdTime(createdTime)
        .build();
  }
}
