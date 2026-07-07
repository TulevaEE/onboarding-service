package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KybMonitoringService {

  private final LegalEntityScreener legalEntityScreener;
  private final CompanyRepository companyRepository;
  private final LatestKybSurveyInputs latestKybSurveyInputs;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void screenAllCompanies() {
    var startedAt = clock.instant();
    var companies = companyRepository.findAll();
    log.info("Starting daily KYB monitoring: companyCount={}", companies.size());
    int successCount = 0;
    int failureCount = 0;
    List<String> skippedWithoutSurvey = new ArrayList<>();

    for (var company : companies) {
      if (!latestKybSurveyInputs.hasSurvey(company.getRegistryCode())) {
        skippedWithoutSurvey.add(company.getRegistryCode());
        continue;
      }
      try {
        legalEntityScreener.screenLatest(company.getRegistryCode());
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.error("KYB monitoring failed: registryCode={}", company.getRegistryCode(), e);
      }
    }
    if (!skippedWithoutSurvey.isEmpty()) {
      log.error(
          "KYB monitoring found onboarded companies with no KYB survey, backfill required for"
              + " compliance: registryCodes={}",
          skippedWithoutSurvey);
    }
    log.info(
        "Daily KYB monitoring completed: successCount={}, failureCount={}, skippedCount={}",
        successCount,
        failureCount,
        skippedWithoutSurvey.size());
    eventPublisher.publishEvent(new KybMonitoringCompletedEvent(startedAt));
  }
}
