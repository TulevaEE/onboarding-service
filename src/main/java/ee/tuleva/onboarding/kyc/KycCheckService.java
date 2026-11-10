package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.survey.KycSurvey;
import ee.tuleva.onboarding.kyc.survey.KycSurveyRepository;
import ee.tuleva.onboarding.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycCheckService {

  private final ApplicationEventPublisher eventPublisher;
  private final KycChecker kycChecker;
  private final KycSurveyRepository kycSurveyRepository;
  private final TransactionTemplate transactionTemplate;

  public void beforeCheck(User subject, Set<Country> countries) {
    eventPublisher.publishEvent(new BeforeKycCheckedEvent(subject, countries));
  }

  public void check(User subject, KycSurvey submitted) {
    var kycCheck = kycChecker.check(subject);
    transactionTemplate.executeWithoutResult(
        status -> publishUnlessANewerSurveyCommitted(subject, submitted, kycCheck));
  }

  private void publishUnlessANewerSurveyCommitted(
      User subject, KycSurvey submitted, KycCheck kycCheck) {
    var latest =
        kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDescIdDesc(subject.getIdOrThrow());
    if (latest.isEmpty()) {
      log.warn(
          "KYC check not published, no KYC survey found although this one was committed:"
              + " userId={}, surveyId={}",
          subject.getId(),
          submitted.getId());
      return;
    }
    if (!latest.get().getId().equals(submitted.getId())) {
      log.warn(
          "KYC check not published, a newer KYC survey committed while it was assessed:"
              + " userId={}, surveyId={}, latestSurveyId={}",
          subject.getId(),
          submitted.getId(),
          latest.get().getId());
      return;
    }
    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(
            this, subject.getPersonalCode(), kycCheck, submitted.getSurvey().purpose()));
  }
}
