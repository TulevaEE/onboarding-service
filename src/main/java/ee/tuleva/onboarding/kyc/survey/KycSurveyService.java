package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.Address;
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
    if (!savingsFundOnboardingService.isWhitelisted(person)) {
      throw new IllegalStateException(
          "Person is not whitelisted: personalCode=" + person.getPersonalCode());
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
                    .filter(Address.class::isInstance)
                    .map(Address.class::cast)
                    .findFirst()
                    .map(address -> new Country(address.value().value().countryCode())));
  }

  private Country extractCountry(KycSurveyResponse surveyResponse) {
    return surveyResponse.answers().stream()
        .filter(item -> item instanceof Address)
        .map(item -> (Address) item)
        .findFirst()
        .map(
            address -> Country.builder().countryCode(address.value().value().countryCode()).build())
        .orElseThrow(() -> new IllegalArgumentException("Country code is required in KYC survey"));
  }
}
