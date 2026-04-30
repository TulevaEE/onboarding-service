package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LatestKybSurveyInputs {

  private final KybSurveyRepository kybSurveyRepository;
  private final KybSurveyResponseMapper kybSurveyResponseMapper;
  private final UserRepository userRepository;

  public KybSurveyInputs findByRegistryCode(String registryCode) {
    var survey =
        kybSurveyRepository
            .findTopByRegistryCodeOrderByCreatedTimeDesc(registryCode)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No KYB survey found for company: registryCode=" + registryCode));

    var user =
        userRepository
            .findById(survey.getUserId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "User not found for KYB survey: registryCode="
                            + registryCode
                            + ", userId="
                            + survey.getUserId()));

    var selfCertification = kybSurveyResponseMapper.extractSelfCertification(survey.getSurvey());

    return new KybSurveyInputs(new PersonalCode(user.getPersonalCode()), selfCertification);
  }
}
