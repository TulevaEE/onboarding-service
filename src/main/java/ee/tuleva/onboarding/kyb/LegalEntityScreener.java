package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybRelationshipRoles.RELATED_PERSON_ROLES;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.RESCREENING;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalEntityScreener {

  private final AriregisterClient ariregisterClient;
  private final KybCompanyDataMapper kybCompanyDataMapper;
  private final KybScreeningService kybScreeningService;
  private final LatestKybSurveyInputs latestKybSurveyInputs;
  private final RelatedPersonsSanctionScreener relatedPersonsSanctionScreener;
  private final Clock clock;

  public CompanyRelationships fetchRelationships(String registryCode) {
    return new CompanyRelationships(
        ariregisterClient.getCompanyRelationships(registryCode).stream()
            .filter(relationship -> RELATED_PERSON_ROLES.contains(relationship.roleCode()))
            .toList(),
        LocalDate.now(clock));
  }

  public List<KybCheck> screen(
      String registryCode,
      PersonalCode personalCode,
      SelfCertification selfCertification,
      CompanyRelationships relationships,
      KybScreeningTrigger trigger) {
    var detail = fetchCompanyDetail(registryCode);
    var beneficialOwners = ariregisterClient.getBeneficialOwners(registryCode);
    var data =
        kybCompanyDataMapper.toKybCompanyData(
            detail, personalCode, relationships, beneficialOwners, selfCertification);
    return kybScreeningService.screen(data, trigger);
  }

  public List<KybCheck> screenLatest(String registryCode) {
    var surveyInputs = latestKybSurveyInputs.findByRegistryCode(registryCode);
    var relationships = fetchRelationships(registryCode);
    return screen(
        registryCode,
        surveyInputs.personalCode(),
        surveyInputs.selfCertification(),
        relationships,
        RESCREENING);
  }

  public void screenRelatedPersons(String registryCode) {
    relatedPersonsSanctionScreener.screen(
        registryCode,
        fetchRelationships(registryCode).active(),
        ariregisterClient.getBeneficialOwners(registryCode));
  }

  public ValidationResult validate(
      String registryCode,
      PersonalCode personalCode,
      @Nullable SelfCertification selfCertification,
      CompanyRelationships relationships) {
    var detail = fetchCompanyDetail(registryCode);
    var beneficialOwners = ariregisterClient.getBeneficialOwners(registryCode);
    var data =
        kybCompanyDataMapper.toKybCompanyData(
            detail, personalCode, relationships, beneficialOwners, selfCertification);
    return new ValidationResult(detail, kybScreeningService.validate(data));
  }

  public record ValidationResult(CompanyDetail detail, List<KybCheck> checks) {}

  private CompanyDetail fetchCompanyDetail(String registryCode) {
    return ariregisterClient
        .getCompanyDetails(registryCode)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Company not found in Ariregister: registryCode=" + registryCode));
  }
}
