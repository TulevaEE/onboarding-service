package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class KybSurveyService {

  private final AriregisterClient ariregisterClient;
  private final KybCompanyDataMapper kybCompanyDataMapper;
  private final KybScreeningService kybScreeningService;
  private final KybSurveyResponseMapper kybSurveyResponseMapper;
  private final KybSurveyRepository kybSurveyRepository;
  private final Clock clock;

  private static final Map<KybCheckType, String> FIELD_MAPPING =
      Map.of(
          KybCheckType.COMPANY_STRUCTURE, "relatedPersons",
          KybCheckType.COMPANY_ACTIVE, "status",
          KybCheckType.HIGH_RISK_NACE, "naceCode",
          KybCheckType.COMPANY_SANCTION, "name",
          KybCheckType.COMPANY_PEP, "name",
          KybCheckType.RELATED_PERSONS_KYC, "relatedPersons");

  private static final String UNSUPPORTED_OWNERSHIP_MESSAGE =
      "Ettevõtte omandistruktuur ei ole toetatud";

  // TODO: review error messages with product
  // TODO: add legal form check — only OÜ is supported
  private static final Map<KybCheckType, String> ERROR_MESSAGES =
      Map.of(
          KybCheckType.COMPANY_STRUCTURE, UNSUPPORTED_OWNERSHIP_MESSAGE,
          KybCheckType.COMPANY_ACTIVE, "Ettevõte ei ole aktiivne",
          KybCheckType.HIGH_RISK_NACE, "See tegevusala ei ole toetatud",
          KybCheckType.COMPANY_SANCTION, "Ettevõtet ei ole võimalik teenindada",
          KybCheckType.COMPANY_PEP, "Ettevõtet ei ole võimalik teenindada",
          KybCheckType.RELATED_PERSONS_KYC,
              "Seotud isikute isikusamasuse tuvastamine on lõpetamata");

  private static final java.util.Set<KybCheckType> OWNERSHIP_CHECKS =
      java.util.Set.of(
          KybCheckType.SOLE_MEMBER_OWNERSHIP,
          KybCheckType.DUAL_MEMBER_OWNERSHIP,
          KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER);

  private static final String BOARD_MEMBER_ROLE = "JUHL";

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

    var checks = kybScreeningService.screen(companyData);

    return buildLegalEntityData(detail, relationships, checks);
  }

  LegalEntityData submit(
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
    var detail = fetchCompanyDetail(registryCode);

    var companyData =
        kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(personalCode), relationships, selfCertification);

    var checks = kybScreeningService.screen(companyData);

    return buildLegalEntityData(detail, relationships, checks);
  }

  private List<CompanyRelationship> fetchRelationshipsAndVerifyBoardMember(
      String registryCode, String personalCode) {
    var relationships =
        ariregisterClient.getActiveCompanyRelationships(registryCode, LocalDate.now(clock));

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

  private LegalEntityData buildLegalEntityData(
      CompanyDetail detail, List<CompanyRelationship> relationships, List<KybCheck> checks) {

    var errorsByField = collectErrorsByField(checks);

    var status =
        detail
            .getStatus()
            .map(CompanyStatus::valueOf)
            .map(LegalEntityStatus::fromCompanyStatus)
            .orElse(null);

    var relatedPersons =
        relationships.stream()
            .map(r -> new RelatedPersonData(r.personalCode(), formatName(r)))
            .distinct()
            .toList();

    return new LegalEntityData(
        validatedField(detail.getName(), errorsByField.getOrDefault("name", List.of())),
        ValidatedField.valid(detail.getRegistryCode()),
        ValidatedField.valid(detail.getLegalForm().orElse(null)),
        ValidatedField.valid(detail.getFoundingDate().orElse(null)),
        validatedField(status, errorsByField.getOrDefault("status", List.of())),
        ValidatedField.valid(
            LegalEntityAddress.fromCompanyAddress(detail.getAddress().orElse(null))),
        ValidatedField.valid(detail.getMainActivity().orElse(null)),
        validatedField(
            detail.getNaceCode().orElse(null), errorsByField.getOrDefault("naceCode", List.of())),
        validatedField(relatedPersons, errorsByField.getOrDefault("relatedPersons", List.of())));
  }

  private Map<String, List<String>> collectErrorsByField(List<KybCheck> checks) {
    var errorsByField = new java.util.HashMap<String, List<String>>();
    checks.stream()
        .filter(check -> !check.success())
        .filter(check -> !OWNERSHIP_CHECKS.contains(check.type()))
        .forEach(
            check -> {
              var field = FIELD_MAPPING.get(check.type());
              if (field != null) {
                errorsByField
                    .computeIfAbsent(field, k -> new ArrayList<>())
                    .add(ERROR_MESSAGES.get(check.type()));
              }
            });

    boolean anyOwnershipCheckPassed =
        checks.stream()
            .filter(check -> OWNERSHIP_CHECKS.contains(check.type()))
            .anyMatch(KybCheck::success);
    if (!anyOwnershipCheckPassed) {
      errorsByField
          .computeIfAbsent("relatedPersons", k -> new ArrayList<>())
          .add(UNSUPPORTED_OWNERSHIP_MESSAGE);
    }

    return errorsByField;
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
