package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KycSurveyService {

  private final KycSurveyRepository kycSurveyRepository;
  private final KycCheckService kycCheckService;
  private final UserService userService;

  @Transactional
  public KycSurvey submit(AuthenticatedPerson person, KycSurveyResponse surveyResponse) {
    User subject = resolveSubject(person);
    KycSurvey survey = KycSurvey.builder().userId(subject.getId()).survey(surveyResponse).build();
    // The risk assessment reads kyc_survey with plain JDBC inside this same
    // transaction, which does not trigger Hibernate's auto-flush — without an
    // explicit flush a first-time submitter's survey is invisible to it.
    KycSurvey saved = kycSurveyRepository.saveAndFlush(survey);

    var country = extractCountry(surveyResponse);
    kycCheckService.check(subject, country, surveyResponse.purpose());

    return saved;
  }

  public KycIdentityResponse getIdentity(AuthenticatedPerson person) {
    User subject = resolveSubject(person);
    return kycSurveyRepository
        .findFirstByUserIdOrderByCreatedTimeDesc(subject.getId())
        .map(
            survey ->
                KycIdentityResponse.from(survey.getSurvey(), survey.getCreatedTime(), subject))
        .orElseGet(() -> KycIdentityResponse.empty(subject));
  }

  public Optional<Country> getCountry(Long userId) {
    return kycSurveyRepository
        .findFirstByUserIdOrderByCreatedTimeDesc(userId)
        .flatMap(survey -> survey.getSurvey().address())
        .map(address -> new Country(address.countryCode()));
  }

  private User resolveSubject(AuthenticatedPerson person) {
    return userService
        .findByPersonalCode(person.getRoleCode())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "KYC subject user not found: roleCode=" + person.getRoleCode()));
  }

  private Country extractCountry(KycSurveyResponse surveyResponse) {
    return surveyResponse
        .address()
        .map(address -> new Country(address.countryCode()))
        .orElseThrow(() -> new IllegalArgumentException("Country code is required in KYC survey"));
  }
}
