package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalEntityScreener {

  private static final String FOUNDER_ROLE = "A";

  private final AriregisterClient ariregisterClient;
  private final KybCompanyDataMapper kybCompanyDataMapper;
  private final KybScreeningService kybScreeningService;
  private final LatestKybSurveyInputs latestKybSurveyInputs;
  private final OwnershipChangeDetector ownershipChangeDetector;
  private final Clock clock;

  public List<CompanyRelationship> fetchActiveRelationships(String registryCode) {
    return ariregisterClient
        .getActiveCompanyRelationships(registryCode, LocalDate.now(clock))
        .stream()
        .filter(r -> !FOUNDER_ROLE.equals(r.roleCode()))
        .toList();
  }

  public List<KybCheck> screen(
      String registryCode,
      PersonalCode personalCode,
      SelfCertification selfCertification,
      List<CompanyRelationship> relationships) {
    var detail = fetchCompanyDetail(registryCode);
    var data =
        kybCompanyDataMapper.toKybCompanyData(
            detail,
            personalCode,
            relationships,
            selfCertification,
            ownerChangedBeforeOnboarding(registryCode));
    return kybScreeningService.screen(data);
  }

  public List<KybCheck> screenLatest(String registryCode) {
    var surveyInputs = latestKybSurveyInputs.findByRegistryCode(registryCode);
    var relationships = fetchActiveRelationships(registryCode);
    return screen(
        registryCode, surveyInputs.personalCode(), surveyInputs.selfCertification(), relationships);
  }

  public ValidationResult validate(
      String registryCode,
      PersonalCode personalCode,
      SelfCertification selfCertification,
      List<CompanyRelationship> relationships) {
    var detail = fetchCompanyDetail(registryCode);
    var data =
        kybCompanyDataMapper.toKybCompanyData(
            detail,
            personalCode,
            relationships,
            selfCertification,
            ownerChangedBeforeOnboarding(registryCode));
    return new ValidationResult(detail, kybScreeningService.validate(data));
  }

  private boolean ownerChangedBeforeOnboarding(String registryCode) {
    return ownershipChangeDetector.ownerChangedBeforeOnboarding(
        ariregisterClient.getCompanyRelationships(registryCode));
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
