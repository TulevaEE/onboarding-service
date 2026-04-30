package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
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
            detail, personalCode, relationships, selfCertification);
    return kybScreeningService.screen(data);
  }

  public ValidationResult validate(
      String registryCode,
      PersonalCode personalCode,
      SelfCertification selfCertification,
      List<CompanyRelationship> relationships) {
    var detail = fetchCompanyDetail(registryCode);
    var data =
        kybCompanyDataMapper.toKybCompanyData(
            detail, personalCode, relationships, selfCertification);
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
