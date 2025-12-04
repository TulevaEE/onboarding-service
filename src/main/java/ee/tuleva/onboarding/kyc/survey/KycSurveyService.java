package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycSurveyService {

  private final KycSurveyRepository kycSurveyRepository;
  private final KycCheckService kycCheckService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;

  public KycSurvey submit(AuthenticatedPerson person, KycSurveyResponse surveyResponse) {
    if (!savingsFundOnboardingService.isUserWhitelisted(person.getUserId())) {
      throw new IllegalStateException("User is not whitelisted.");
    }
    KycSurvey survey =
        KycSurvey.builder().userId(person.getUserId()).survey(surveyResponse).build();
    KycSurvey saved = kycSurveyRepository.save(survey);

    var country = extractCountry(surveyResponse);
    kycCheckService.check(person, country);

    return saved;
  }

  public Optional<Country> getCountry(Long userId) {
    return kycSurveyRepository
        .findFirstByUserIdOrderByCreatedTimeDesc(userId)
        .flatMap(
            survey ->
                survey.getSurvey().answers().stream()
                    .filter(KycSurveyResponseItem.Address.class::isInstance)
                    .map(KycSurveyResponseItem.Address.class::cast)
                    .findFirst()
                    .map(address -> new Country(address.value().value().countryCode())));
  }

  private Country extractCountry(KycSurveyResponse surveyResponse) {
    return surveyResponse.answers().stream()
        .filter(item -> item instanceof KycSurveyResponseItem.Address)
        .map(item -> (KycSurveyResponseItem.Address) item)
        .findFirst()
        .map(addr -> Country.builder().countryCode(addr.value().value().countryCode()).build())
        .orElseThrow(() -> new IllegalArgumentException("Address is required in KYC survey"));
  }
}
