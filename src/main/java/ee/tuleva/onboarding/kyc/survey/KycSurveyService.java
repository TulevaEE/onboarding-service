package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.user.address.Address;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycSurveyService {

  private final KycSurveyRepository kycSurveyRepository;
  private final KycCheckService kycCheckService;

  public KycSurvey submit(AuthenticatedPerson person, KycSurveyResponse surveyResponse) {
    KycSurvey survey =
        KycSurvey.builder().userId(person.getUserId()).survey(surveyResponse).build();
    KycSurvey saved = kycSurveyRepository.save(survey);

    var address = extractAddress(surveyResponse);
    kycCheckService.check(person, address);

    return saved;
  }

  private Address extractAddress(KycSurveyResponse surveyResponse) {
    return surveyResponse.answers().stream()
        .filter(item -> item instanceof KycSurveyResponseItem.Address)
        .map(item -> (KycSurveyResponseItem.Address) item)
        .findFirst()
        .map(addr -> Address.builder().countryCode(addr.value().value().countryCode()).build())
        .orElseThrow(() -> new IllegalArgumentException("Address is required in KYC survey"));
  }
}
