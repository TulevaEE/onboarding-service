package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KybMonitoringService {

  private final LegalEntityScreener legalEntityScreener;
  private final LatestKybSurveyInputs latestKybSurveyInputs;
  private final CompanyRepository companyRepository;

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
    var surveyInputs = latestKybSurveyInputs.findByRegistryCode(registryCode);
    var relationships = legalEntityScreener.fetchActiveRelationships(registryCode);
    legalEntityScreener.screen(
        registryCode, surveyInputs.personalCode(), surveyInputs.selfCertification(), relationships);
  }
}
