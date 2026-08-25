package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.survey.KycSurvey;
import ee.tuleva.onboarding.kyc.survey.KycSurveyRepository;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponse;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class KycCountryService {

  private final KycSurveyRepository kycSurveyRepository;

  public Optional<Set<Country>> getCountries(Long userId) {
    return kycSurveyRepository
        .findFirstByUserIdOrderByCreatedTimeDesc(userId)
        .map(KycSurvey::getSurvey)
        .filter(survey -> survey.address().isPresent())
        .map(KycSurveyResponse::countries);
  }
}
