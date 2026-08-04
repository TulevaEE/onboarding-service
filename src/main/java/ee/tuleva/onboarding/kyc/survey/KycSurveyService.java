package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
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

    kycCheckService.check(subject, extractCountries(surveyResponse), surveyResponse.purpose());

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

  public Optional<Set<Country>> getCountries(Long userId) {
    return kycSurveyRepository
        .findFirstByUserIdOrderByCreatedTimeDesc(userId)
        .map(KycSurvey::getSurvey)
        .filter(survey -> survey.address().isPresent())
        .map(this::extractCountries);
  }

  private User resolveSubject(AuthenticatedPerson person) {
    String subjectPersonalCode =
        person.isLegalEntity() ? person.getPersonalCode() : person.getRoleCode();
    return userService
        .findByPersonalCode(subjectPersonalCode)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "KYC subject user not found: personalCode=" + subjectPersonalCode));
  }

  private Set<Country> extractCountries(KycSurveyResponse surveyResponse) {
    String residence =
        surveyResponse
            .address()
            .map(KycSurveyResponseItem.AddressDetails::countryCode)
            .orElseThrow(
                () -> new IllegalArgumentException("Country code is required in KYC survey"));
    return Countries.of(
        Stream.concat(Stream.of(residence), surveyResponse.citizenship().orElse(List.of()).stream())
            .toList());
  }
}
