package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.survey.KybCompanyDataMapper;
import ee.tuleva.onboarding.kyb.survey.KybSurveyDataProvider;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KybMonitoringService {

  private final AriregisterClient ariregisterClient;
  private final KybCompanyDataMapper kybCompanyDataMapper;
  private final KybSurveyDataProvider kybSurveyDataProvider;
  private final KybScreeningService kybScreeningService;
  private final CompanyRepository companyRepository;
  private final Clock clock;

  public void screenAllCompanies() {
    var companies = companyRepository.findAll();
    log.info("Starting daily KYB monitoring: companyCount={}", companies.size());
    int successCount = 0;
    int failureCount = 0;

    for (var company : companies) {
      try {
        screenCompany(company.getRegistryCode());
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.error("KYB monitoring failed: registryCode={}", company.getRegistryCode(), e);
      }
    }
    log.info(
        "Daily KYB monitoring completed: successCount={}, failureCount={}",
        successCount,
        failureCount);
  }

  void screenCompany(String registryCode) {
    var surveyData = kybSurveyDataProvider.getLatestByRegistryCode(registryCode);

    var relationships =
        ariregisterClient.getActiveCompanyRelationships(registryCode, LocalDate.now(clock));
    var detail =
        ariregisterClient
            .getCompanyDetails(registryCode)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Company not found in Ariregister: registryCode=" + registryCode));

    var companyData =
        kybCompanyDataMapper.toKybCompanyData(
            detail, surveyData.personalCode(), relationships, surveyData.selfCertification());

    kybScreeningService.screen(companyData);
  }
}
