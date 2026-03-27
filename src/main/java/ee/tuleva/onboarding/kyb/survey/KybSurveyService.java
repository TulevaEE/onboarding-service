package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.WHITELISTED;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private static final String BOARD_MEMBER_ROLE = "JUHL";
  // Founders (role "A") are historical — they don't imply any current relationship with the company
  private static final String FOUNDER_ROLE = "A";

  LegalEntityData initialValidation(String registryCode, String personalCode) {
    log.info(
        "Initial validation for legal entity: registryCode={}, personalCode={}",
        registryCode,
        personalCode);

    var relationships = fetchRelationshipsAndVerifyBoardMember(registryCode, personalCode);
    var detail = fetchCompanyDetail(registryCode);

    var companyData =
        kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(personalCode), relationships, null);

    var checks = kybScreeningService.validate(companyData);

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

    var relationships = fetchRelationshipsAndVerifyBoardMember(registryCode, personalCode);
    verifyOnboardingAllowed(registryCode);
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

  private Optional<String> getOnboardingError(String registryCode) {
    var status = savingsFundOnboardingRepository.findStatusByPersonalCode(registryCode);
    if (status.filter(s -> s != WHITELISTED && s != REJECTED).isPresent()) {
      return Optional.of("Ettevõte on juba liitunud");
    }
    if (!Instant.now(clock).isBefore(WHITELIST_CUTOFF)
        && status.filter(s -> s == WHITELISTED).isEmpty()) {
      return Optional.of("Ettevõttel ei ole eelheakskiitu");
    }
    return Optional.empty();
  }

  private void verifyOnboardingAllowed(String registryCode) {
    getOnboardingError(registryCode)
        .ifPresent(
            error -> {
              throw new OnboardingNotAllowedException(registryCode);
            });
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
