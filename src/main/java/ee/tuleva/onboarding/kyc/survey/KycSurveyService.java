package ee.tuleva.onboarding.kyc.survey;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycSurveyService {

  private final KycSurveyRepository kycSurveyRepository;

  public KycSurvey save(Long userId, KycSurveyResponse surveyResponse) {
    KycSurvey survey = KycSurvey.builder().userId(userId).survey(surveyResponse).build();
    return kycSurveyRepository.save(survey);
  }
}
