package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.SUBMISSION;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.NOT_BOARD_MEMBER;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class KybSurveyService {

  private final LegalEntityScreener legalEntityScreener;
  private final KybSurveyResponseMapper kybSurveyResponseMapper;
  private final KybSurveyRepository kybSurveyRepository;
  private final OnboardingGate onboardingGate;
  private final ApplicationEventPublisher eventPublisher;

  private static final String BOARD_MEMBER_ROLE = "JUHL";

  LegalEntityData initialValidation(String registryCode, String personalCode) {
    log.info(
        "Initial validation for legal entity: registryCode={}, personalCode={}",
        registryCode,
        personalCode);

    List<CompanyRelationship> relationships;
    try {
      relationships = fetchAndVerifyBoardMember(registryCode, personalCode);
    } catch (NotBoardMemberException e) {
      onboardingGate.auditBlocked(registryCode, personalCode, NOT_BOARD_MEMBER);
      throw e;
    }

    var result =
        legalEntityScreener.validate(
            registryCode, new PersonalCode(personalCode), null, relationships);

    auditValidationFailures(registryCode, personalCode, result.checks());

    return buildLegalEntityData(
        result.detail(),
        relationships,
        result.checks(),
        personalCode,
        onboardingGate.getOnboardingError(registryCode));
  }

  void submit(
      Long userId, String personalCode, String registryCode, KybSurveyResponse surveyResponse) {
    var selfCertification = kybSurveyResponseMapper.extractSelfCertification(surveyResponse);

    log.info("Submitting KYB survey: registryCode={}, personalCode={}", registryCode, personalCode);

    List<CompanyRelationship> relationships;
    try {
      relationships = fetchAndVerifyBoardMember(registryCode, personalCode);
    } catch (NotBoardMemberException e) {
      onboardingGate.auditBlocked(registryCode, personalCode, NOT_BOARD_MEMBER);
      throw e;
    }

    onboardingGate.verifyOnboardingAllowed(registryCode, personalCode);

    // LatestKybSurveyInputs reads the newest survey for a company, so a rejected
    // submission must not leave one behind.
    kybSurveyRepository.save(
        KybSurvey.builder()
            .userId(userId)
            .registryCode(registryCode)
            .survey(surveyResponse)
            .build());

    // Only a rejected or unknown company gets this far, and the SUBMISSION trigger is what lets the
    // screening below queue a rejected company whose related persons are still unverified.
    legalEntityScreener.screen(
        registryCode, new PersonalCode(personalCode), selfCertification, relationships, SUBMISSION);
  }

  private List<CompanyRelationship> fetchAndVerifyBoardMember(
      String registryCode, String personalCode) {
    var relationships = legalEntityScreener.fetchActiveRelationships(registryCode);

    boolean isBoardMember =
        relationships.stream()
            .anyMatch(
                r ->
                    BOARD_MEMBER_ROLE.equals(r.roleCode())
                        && personalCode.equals(r.personalCode()));
    if (!isBoardMember) {
      throw new NotBoardMemberException(registryCode, personalCode);
    }

    return relationships;
  }

  private void auditValidationFailures(
      String registryCode, String personalCode, List<KybCheck> checks) {
    if (checks.stream().allMatch(c -> !c.type().isOnboardingGate() || c.success())) {
      return;
    }

    log.warn(
        "Initial validation failed: registryCode={}, personalCode={}, failedChecks={}",
        registryCode,
        personalCode,
        checks.stream()
            .filter(c -> c.type().isOnboardingGate() && !c.success())
            .map(c -> c.type().name())
            .collect(joining(",")));

    var data = new LinkedHashMap<String, @Nullable Object>();
    data.put("partyType", "LEGAL_ENTITY");
    data.put("registryCode", registryCode);
    data.put("personalCode", personalCode);
    data.put("outcome", "VALIDATION_FAILED");
    data.put(
        "checks",
        checks.stream()
            .map(
                c ->
                    Map.of(
                        "type", c.type().name(),
                        "success", c.success(),
                        "metadata", c.metadata()))
            .toList());

    eventPublisher.publishEvent(
        new TrackableSystemEvent(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, data));
  }

  private LegalEntityData buildLegalEntityData(
      CompanyDetail detail,
      List<CompanyRelationship> relationships,
      List<KybCheck> checks,
      String userPersonalCode,
      Optional<ValidationError> onboardingError) {

    var relatedPersons = KybCheckFieldErrors.dedupedByPersonalCode(relationships);

    var errorsByField =
        KybCheckFieldErrors.collectErrorsByField(checks, userPersonalCode, relatedPersons);

    var nameErrors = new ArrayList<>(errorsByField.getOrDefault("name", List.of()));
    onboardingError.ifPresent(nameErrors::add);

    var status =
        detail
            .getStatus()
            .map(CompanyStatus::valueOf)
            .map(LegalEntityStatus::fromCompanyStatus)
            .orElse(null);

    return new LegalEntityData(
        KybCheckFieldErrors.validatedField(detail.getName(), nameErrors),
        ValidatedField.valid(detail.getRegistryCode()),
        KybCheckFieldErrors.validatedField(
            detail.getLegalForm().orElse(null), errorsByField.getOrDefault("legalForm", List.of())),
        ValidatedField.valid(detail.getFoundingDate().orElse(null)),
        KybCheckFieldErrors.validatedField(status, errorsByField.getOrDefault("status", List.of())),
        KybCheckFieldErrors.validatedField(
            LegalEntityAddress.fromCompanyAddress(detail.getAddress().orElse(null)),
            errorsByField.getOrDefault("address", List.of())),
        ValidatedField.valid(detail.getMainActivity().orElse(null)),
        KybCheckFieldErrors.validatedField(
            detail.getNaceCode().orElse(null), errorsByField.getOrDefault("naceCode", List.of())),
        KybCheckFieldErrors.validatedField(
            relatedPersons, errorsByField.getOrDefault("relatedPersons", List.of())));
  }
}
