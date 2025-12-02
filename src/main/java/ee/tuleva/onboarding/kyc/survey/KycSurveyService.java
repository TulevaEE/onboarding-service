package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.kyc.KycCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycSurveyService {

  private final KycSurveyRepository kycSurveyRepository;
  private final KycCheckService kycCheckService;
  private final ApplicationEventPublisher eventPublisher;

  public KycSurvey submit(AuthenticatedPerson person, KycSurveyResponse surveyResponse) {
    KycSurvey survey =
        KycSurvey.builder().userId(person.getUserId()).survey(surveyResponse).build();
    KycSurvey saved = kycSurveyRepository.save(survey);

    var kycCheck = kycCheckService.check(person.getPersonalCode());
    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(this, person.getPersonalCode(), kycCheck));

    return saved;
  }
}
