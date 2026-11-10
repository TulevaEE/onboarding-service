package ee.tuleva.onboarding.kyc.survey;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class KycSurveyService {

  private final KycSurveyRepository kycSurveyRepository;
  private final KycCheckService kycCheckService;
  private final UserService userService;
  private final TransactionTemplate commitBeforeAmlServiceAssesses;

  public KycSurvey submit(AuthenticatedPerson person, KycSurveyResponse surveyResponse) {
    User subject = resolveSubject(person);
    KycSurvey saved =
        requireNonNull(
            commitBeforeAmlServiceAssesses.execute(
                status -> saveSurveyAndScreenings(subject, surveyResponse)));
    kycCheckService.check(subject, saved);
    return saved;
  }

  private KycSurvey saveSurveyAndScreenings(User subject, KycSurveyResponse surveyResponse) {
    KycSurvey saved =
        kycSurveyRepository.save(
            KycSurvey.builder().userId(subject.getIdOrThrow()).survey(surveyResponse).build());
    kycCheckService.beforeCheck(subject, surveyResponse.countries());
    return saved;
  }

  public KycIdentityResponse getIdentity(AuthenticatedPerson person) {
    User subject = resolveSubject(person);
    return kycSurveyRepository
        .findFirstByUserIdOrderByCreatedTimeDescIdDesc(subject.getIdOrThrow())
        .map(
            survey ->
                KycIdentityResponse.from(survey.getSurvey(), survey.getCreatedTime(), subject))
        .orElseGet(() -> KycIdentityResponse.empty(subject));
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
}
