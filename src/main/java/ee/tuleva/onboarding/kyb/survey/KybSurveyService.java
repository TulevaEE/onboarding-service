package ee.tuleva.onboarding.kyb.survey;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
      case DATA_CHANGED -> new FieldError("name", "Ettevõtet ei ole võimalik teenindada");
      case SELF_CERTIFICATION -> null;
    };
  }

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
    var detail = fetchCompanyDetail(registryCode);

    var companyData =
        kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(personalCode), relationships, selfCertification);

    kybScreeningService.screen(companyData);
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
