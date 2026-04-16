package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.ALREADY_ONBOARDED;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.NOT_BOARD_MEMBER;
import static ee.tuleva.onboarding.kyb.survey.BlockedReason.NO_WHITELIST_AFTER_CUTOFF;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.WHITELISTED;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.*;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class KybSurveyService {

  private static final Instant WHITELIST_CUTOFF = Instant.parse("2026-03-27T15:00:00Z");

  private final AriregisterClient ariregisterClient;
  private final KybCompanyDataMapper kybCompanyDataMapper;
  private final KybScreeningService kybScreeningService;
  private final KybSurveyResponseMapper kybSurveyResponseMapper;
  private final KybSurveyRepository kybSurveyRepository;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  private record FieldError(String field, String message) {}

  private static FieldError fieldErrorFor(KybCheckType type) {
    return switch (type) {
      case COMPANY_ACTIVE -> new FieldError("status", "Ettevõte ei ole aktiivne");
      case HIGH_RISK_NACE -> new FieldError("naceCode", "See tegevusala ei ole toetatud");
      case COMPANY_SANCTION -> new FieldError("name", "Ettevõtet ei ole võimalik teenindada");
      case COMPANY_PEP -> new FieldError("name", "Ettevõtet ei ole võimalik teenindada");
      case RELATED_PERSONS_KYC ->
          new FieldError(
              "relatedPersons", "Seotud isikute isikusamasuse tuvastamine on lõpetamata");
      case COMPANY_LEGAL_FORM -> new FieldError("legalForm", "Ainult OÜ on toetatud");
      case COMPANY_STRUCTURE,
          SOLE_MEMBER_OWNERSHIP,
          DUAL_MEMBER_OWNERSHIP,
          SOLE_BOARD_MEMBER_IS_OWNER ->
          new FieldError("relatedPersons", "Ettevõtte omandistruktuur ei ole toetatud");
      case DATA_CHANGED, SELF_CERTIFICATION -> null;
    };
  }

  private static String blockedReasonMessage(BlockedReason reason) {
    return switch (reason) {
      case ALREADY_ONBOARDED -> "Ettevõte on juba liitunud";
      case NO_WHITELIST_AFTER_CUTOFF -> "Ettevõttel ei ole eelheakskiitu";
      case NOT_BOARD_MEMBER -> "Isik ei ole ettevõtte juhatuse liige";
    };
  }

  private static final String BOARD_MEMBER_ROLE = "JUHL";
  // Founders (role "A") are historical — they don't imply any current relationship with the company
  private static final String FOUNDER_ROLE = "A";

  LegalEntityData initialValidation(String registryCode, String personalCode) {
    log.info(
        "Initial validation for legal entity: registryCode={}, personalCode={}",
        registryCode,
        personalCode);

    List<CompanyRelationship> relationships;
    try {
      relationships = fetchRelationshipsAndVerifyBoardMember(registryCode, personalCode);
    } catch (NotBoardMemberException e) {
      auditBlocked(registryCode, personalCode, NOT_BOARD_MEMBER);
      throw e;
    }

    var detail = fetchCompanyDetail(registryCode);

    var companyData =
        kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(personalCode), relationships, null);

    var checks = kybScreeningService.validate(companyData);

    auditValidationFailures(registryCode, personalCode, checks);

    return buildLegalEntityData(detail, relationships, checks, getOnboardingError(registryCode));
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
      relationships = fetchRelationshipsAndVerifyBoardMember(registryCode, personalCode);
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

    var detail = fetchCompanyDetail(registryCode);

    var companyData =
        kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(personalCode), relationships, selfCertification);

    kybScreeningService.screen(companyData);
  }

  private List<CompanyRelationship> fetchRelationshipsAndVerifyBoardMember(
      String registryCode, String personalCode) {
    var relationships =
        ariregisterClient.getActiveCompanyRelationships(registryCode, LocalDate.now(clock)).stream()
            .filter(r -> !FOUNDER_ROLE.equals(r.roleCode()))
            .toList();

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

  private CompanyDetail fetchCompanyDetail(String registryCode) {
    return ariregisterClient
        .getCompanyDetails(registryCode)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Company not found in Ariregister: registryCode=" + registryCode));
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

  private Optional<String> getOnboardingError(String registryCode) {
    return getBlockedReason(registryCode).map(KybSurveyService::blockedReasonMessage);
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
    if (checks.stream().allMatch(c -> c.type() == DATA_CHANGED || c.success())) {
      return;
    }

    log.warn(
        "Initial validation failed: registryCode={}, personalCode={}, failedChecks={}",
        registryCode,
        personalCode,
        checks.stream()
            .filter(c -> c.type() != DATA_CHANGED && !c.success())
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
      Optional<String> onboardingError) {

    var errorsByField = collectErrorsByField(checks);

    var nameErrors = new ArrayList<>(errorsByField.getOrDefault("name", List.of()));
    onboardingError.ifPresent(nameErrors::add);

    var status =
        detail
            .getStatus()
            .map(CompanyStatus::valueOf)
            .map(LegalEntityStatus::fromCompanyStatus)
            .orElse(null);

    var relatedPersons =
        relationships.stream()
            .map(r -> new RelatedPersonData(r.personalCode(), formatName(r)))
            .collect(groupingBy(RelatedPersonData::personalCode))
            .values()
            .stream()
            .map(List::getFirst)
            .toList();

    return new LegalEntityData(
        validatedField(detail.getName(), nameErrors),
        ValidatedField.valid(detail.getRegistryCode()),
        validatedField(
            detail.getLegalForm().orElse(null), errorsByField.getOrDefault("legalForm", List.of())),
        ValidatedField.valid(detail.getFoundingDate().orElse(null)),
        validatedField(status, errorsByField.getOrDefault("status", List.of())),
        ValidatedField.valid(
            LegalEntityAddress.fromCompanyAddress(detail.getAddress().orElse(null))),
        ValidatedField.valid(detail.getMainActivity().orElse(null)),
        validatedField(
            detail.getNaceCode().orElse(null), errorsByField.getOrDefault("naceCode", List.of())),
        validatedField(relatedPersons, errorsByField.getOrDefault("relatedPersons", List.of())));
  }

  private static Map<String, List<String>> collectErrorsByField(List<KybCheck> checks) {
    return checks.stream()
        .filter(check -> !check.success())
        .map(check -> fieldErrorFor(check.type()))
        .filter(Objects::nonNull)
        .collect(groupingBy(FieldError::field, mapping(FieldError::message, toList())));
  }

  private static <T> ValidatedField<T> validatedField(T value, List<String> errors) {
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
