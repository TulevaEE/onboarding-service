package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.kyb.KybCheckOverride;
import ee.tuleva.onboarding.kyb.KybCheckOverrideRepository;
import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanySourceOfIncome;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualCompanyOnboardingService {

  private static final String CREATED_BY = "admin";
  private static final String BOARD_MEMBER_ROLE = "JUHL";

  private final KybSurveyRepository kybSurveyRepository;
  private final KybCheckOverrideRepository kybCheckOverrideRepository;
  private final LegalEntityScreener legalEntityScreener;
  private final UserRepository userRepository;

  @Transactional
  public void onboard(
      String registryCode, String personalCode, List<KybCheckType> forcedChecks, String reason) {
    var user = verifyBoardMemberAndFindUser(registryCode, personalCode);

    log.info(
        "Manually onboarding company: registryCode={}, personalCode={}, forcedChecks={}, reason={}",
        registryCode,
        personalCode,
        forcedChecks,
        reason);

    kybSurveyRepository.save(
        KybSurvey.builder()
            .userId(user.getId())
            .registryCode(registryCode)
            .survey(nonAttestingSurveyResponse())
            .build());

    forcedChecks.forEach(checkType -> saveOverride(registryCode, checkType, reason));

    legalEntityScreener.screenLatest(registryCode);
  }

  private User verifyBoardMemberAndFindUser(String registryCode, String personalCode) {
    boolean isBoardMember =
        legalEntityScreener.fetchActiveRelationships(registryCode).stream()
            .anyMatch(
                r ->
                    BOARD_MEMBER_ROLE.equals(r.roleCode())
                        && personalCode.equals(r.personalCode()));
    if (!isBoardMember) {
      throw new NotBoardMemberException(registryCode, personalCode);
    }
    return userRepository
        .findByPersonalCode(personalCode)
        .orElseThrow(
            () -> new IllegalArgumentException("No user found for personalCode=" + personalCode));
  }

  private void saveOverride(String registryCode, KybCheckType checkType, String reason) {
    var override =
        kybCheckOverrideRepository
            .findByRegistryCodeAndCheckType(registryCode, checkType)
            .orElseGet(
                () ->
                    KybCheckOverride.builder()
                        .registryCode(registryCode)
                        .checkType(checkType)
                        .build());
    override.setForcedSuccess(true);
    override.setReason(reason);
    override.setCreatedBy(CREATED_BY);
    kybCheckOverrideRepository.save(override);
  }

  private KybSurveyResponse nonAttestingSurveyResponse() {
    return new KybSurveyResponse(List.of(new CompanySourceOfIncome(List.of())));
  }
}
