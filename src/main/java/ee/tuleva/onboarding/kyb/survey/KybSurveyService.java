package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.ALREADY_ONBOARDED;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.NOT_BOARD_MEMBER;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.NO_WHITELIST_AFTER_CUTOFF;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.WHITELISTED;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.*;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class KybSurveyService {

  private static final Instant WHITELIST_CUTOFF = Instant.parse("2026-03-27T15:00:00Z");

  private final LegalEntityScreener legalEntityScreener;
  private final KybSurveyResponseMapper kybSurveyResponseMapper;
  private final KybSurveyRepository kybSurveyRepository;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  private record FieldError(String field, ValidationError error) {}

  private static final String KYC_MESSAGE = "Isikusamasuse tuvastamine on lõpetamata";
  private static final String USER_KYC_MESSAGE = "Sinu isikusamasuse tuvastamine on lõpetamata";

  // Projects a failed check to its client-facing field error(s). Each arm assigns a curated,
  // client-safe code; the internal KybCheckType never reaches the wire. SANCTION and PEP collapse
  // to one opaque code+message so a sanctions hit cannot be told apart from a PEP flag.
  // RELATED_PERSONS_KYC needs runtime context (which persons are incomplete, and whether one is the
  // onboarding user) and can yield two codes, so it is delegated to its own method.
  private static Stream<FieldError> fieldErrorsFor(
      KybCheck check, String userPersonalCode, List<RelatedPersonData> relatedPersons) {
    return switch (check.type()) {
      case COMPANY_ACTIVE ->
          Stream.of(fieldError("status", "COMPANY_ACTIVE", "Ettevõte ei ole aktiivne"));
      case HIGH_RISK_NACE ->
          Stream.of(fieldError("naceCode", "UNSUPPORTED_NACE", "See tegevusala ei ole toetatud"));
      case COMPANY_SANCTION, COMPANY_PEP ->
          Stream.of(fieldError("name", "UNSERVICEABLE", "Ettevõtet ei ole võimalik teenindada"));
      case RELATED_PERSONS_KYC -> relatedPersonsKycErrors(check, userPersonalCode, relatedPersons);
      case COMPANY_LEGAL_FORM ->
          Stream.of(fieldError("legalForm", "COMPANY_LEGAL_FORM", "Ainult OÜ on toetatud"));
      case COMPANY_REGISTERED_IN_ESTONIA ->
          Stream.of(
              fieldError(
                  "address",
                  "COMPANY_REGISTERED_IN_ESTONIA",
                  "Ettevõte ei ole registreeritud Eestis"));
      case COMPANY_STRUCTURE,
          SOLE_MEMBER_OWNERSHIP,
          DUAL_MEMBER_OWNERSHIP,
          SOLE_BOARD_MEMBER_IS_OWNER ->
          Stream.of(
              fieldError(
                  "relatedPersons",
                  "COMPANY_STRUCTURE",
                  "Ettevõtte omandistruktuur ei ole toetatud"));
      case DATA_CHANGED, SELF_CERTIFICATION, COMPANY_AGE -> Stream.of();
    };
  }

  private static FieldError fieldError(String field, String code, String message) {
    return new FieldError(field, new ValidationError(code, message));
  }

  private static Stream<FieldError> relatedPersonsKycErrors(
      KybCheck check, String userPersonalCode, List<RelatedPersonData> relatedPersons) {
    var incompletePersonalCodes = RelatedPersonsKycMetadata.incompletePersonalCodes(check);
    var userIncomplete = incompletePersonalCodes.contains(userPersonalCode);
    var otherPersonalCodes =
        incompletePersonalCodes.stream().filter(code -> !code.equals(userPersonalCode)).toList();

    var errors = new ArrayList<FieldError>();
    if (userIncomplete) {
      errors.add(fieldError("relatedPersons", "USER_KYC", USER_KYC_MESSAGE));
    }
    if (!otherPersonalCodes.isEmpty()) {
      errors.add(
          fieldError(
              "relatedPersons",
              "OTHER_RELATED_PERSONS_KYC",
              kycMessageWithNames(otherPersonalCodes, relatedPersons)));
    }
    if (errors.isEmpty()) {
      errors.add(fieldError("relatedPersons", "OTHER_RELATED_PERSONS_KYC", KYC_MESSAGE));
    }
    return errors.stream();
  }

  private static String kycMessageWithNames(
      List<String> personalCodes, List<RelatedPersonData> relatedPersons) {
    var namesByPersonalCode =
        relatedPersons.stream()
            .filter(person -> person.personalCode() != null && person.name() != null)
            .collect(toMap(RelatedPersonData::personalCode, RelatedPersonData::name, (a, b) -> a));
    var names =
        personalCodes.stream()
            .map(personalCode -> namesByPersonalCode.getOrDefault(personalCode, personalCode))
            .distinct()
            .collect(joining(", "));
    return names.isBlank() ? KYC_MESSAGE : KYC_MESSAGE + ": " + names;
  }

  private static ValidationError blockedReasonError(BlockedReason reason) {
    return new ValidationError(reason.name(), blockedReasonMessage(reason));
  }

  private static String blockedReasonMessage(BlockedReason reason) {
    return switch (reason) {
      case ALREADY_ONBOARDED -> "Ettevõte on juba liitunud";
      case NO_WHITELIST_AFTER_CUTOFF -> "Ettevõttel ei ole eelheakskiitu";
      case NOT_BOARD_MEMBER -> "Isik ei ole ettevõtte juhatuse liige";
    };
  }

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
      auditBlocked(registryCode, personalCode, NOT_BOARD_MEMBER);
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
        getOnboardingError(registryCode));
  }

  void submit(
      Long userId, String personalCode, String registryCode, KybSurveyResponse surveyResponse) {
    var selfCertification = kybSurveyResponseMapper.extractSelfCertification(surveyResponse);

    log.info("Submitting KYB survey: registryCode={}, personalCode={}", registryCode, personalCode);

    kybSurveyRepository.save(
        KybSurvey.builder()
            .userId(userId)
            .registryCode(registryCode)
            .survey(surveyResponse)
            .build());

    List<CompanyRelationship> relationships;
    try {
      relationships = fetchAndVerifyBoardMember(registryCode, personalCode);
    } catch (NotBoardMemberException e) {
      auditBlocked(registryCode, personalCode, NOT_BOARD_MEMBER);
      throw e;
    }

    try {
      verifyOnboardingAllowed(registryCode);
    } catch (OnboardingNotAllowedException e) {
      auditBlocked(registryCode, personalCode, e.getReason());
      throw e;
    }

    legalEntityScreener.screen(
        registryCode, new PersonalCode(personalCode), selfCertification, relationships);
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

  private Optional<BlockedReason> getBlockedReason(String registryCode) {
    var status = savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY);
    if (status.filter(s -> s != WHITELISTED && s != REJECTED).isPresent()) {
      return Optional.of(ALREADY_ONBOARDED);
    }
    if (!Instant.now(clock).isBefore(WHITELIST_CUTOFF)
        && status.filter(s -> s == WHITELISTED).isEmpty()) {
      return Optional.of(NO_WHITELIST_AFTER_CUTOFF);
    }
    return Optional.empty();
  }

  private Optional<ValidationError> getOnboardingError(String registryCode) {
    return getBlockedReason(registryCode).map(KybSurveyService::blockedReasonError);
  }

  private void verifyOnboardingAllowed(String registryCode) {
    getBlockedReason(registryCode)
        .ifPresent(
            reason -> {
              throw new OnboardingNotAllowedException(registryCode, reason);
            });
  }

  private void auditBlocked(String registryCode, String personalCode, BlockedReason reason) {
    log.warn(
        "Company onboarding blocked: registryCode={}, personalCode={}, reason={}",
        registryCode,
        personalCode,
        reason);

    var data = new LinkedHashMap<String, Object>();
    data.put("partyType", LEGAL_ENTITY.name());
    data.put("registryCode", registryCode);
    data.put("personalCode", personalCode);
    data.put("outcome", "BLOCKED");
    data.put("blockedReason", reason.name());

    eventPublisher.publishEvent(
        new TrackableSystemEvent(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, data));
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

    var data = new LinkedHashMap<String, Object>();
    data.put("partyType", LEGAL_ENTITY.name());
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

    var relatedPersons = dedupedByPersonalCode(relationships);

    var errorsByField = collectErrorsByField(checks, userPersonalCode, relatedPersons);

    var nameErrors = new ArrayList<>(errorsByField.getOrDefault("name", List.of()));
    onboardingError.ifPresent(nameErrors::add);

    var status =
        detail
            .getStatus()
            .map(CompanyStatus::valueOf)
            .map(LegalEntityStatus::fromCompanyStatus)
            .orElse(null);

    return new LegalEntityData(
        validatedField(detail.getName(), nameErrors),
        ValidatedField.valid(detail.getRegistryCode()),
        validatedField(
            detail.getLegalForm().orElse(null), errorsByField.getOrDefault("legalForm", List.of())),
        ValidatedField.valid(detail.getFoundingDate().orElse(null)),
        validatedField(status, errorsByField.getOrDefault("status", List.of())),
        validatedField(
            LegalEntityAddress.fromCompanyAddress(detail.getAddress().orElse(null)),
            errorsByField.getOrDefault("address", List.of())),
        ValidatedField.valid(detail.getMainActivity().orElse(null)),
        validatedField(
            detail.getNaceCode().orElse(null), errorsByField.getOrDefault("naceCode", List.of())),
        validatedField(relatedPersons, errorsByField.getOrDefault("relatedPersons", List.of())));
  }

  private static List<RelatedPersonData> dedupedByPersonalCode(
      List<CompanyRelationship> relationships) {
    return relationships.stream()
        .map(r -> new RelatedPersonData(r.personalCode(), formatName(r)))
        .collect(
            toMap(RelatedPersonData::personalCode, identity(), (a, b) -> a, LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private static Map<String, List<ValidationError>> collectErrorsByField(
      List<KybCheck> checks, String userPersonalCode, List<RelatedPersonData> relatedPersons) {
    return checks.stream()
        .filter(check -> !check.success())
        .flatMap(check -> fieldErrorsFor(check, userPersonalCode, relatedPersons))
        .collect(groupingBy(FieldError::field, mapping(FieldError::error, toList())));
  }

  private static <T> ValidatedField<T> validatedField(T value, List<ValidationError> errors) {
    return errors.isEmpty()
        ? ValidatedField.valid(value)
        : ValidatedField.withErrors(value, errors);
  }

  private static String formatName(CompanyRelationship r) {
    if (r.firstName() != null && r.lastName() != null) {
      return r.firstName() + " " + r.lastName();
    }
    return r.lastName();
  }
}
