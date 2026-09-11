package ee.tuleva.onboarding.kyb;

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
  private final OnboardedCompanies onboardedCompanies;
  private final LatestKybSurveyInputs latestKybSurveyInputs;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void screenAllCompanies() {
    var startedAt = clock.instant();
    var registryCodes = onboardedCompanies.registryCodes();
    log.info("Starting daily KYB monitoring: companyCount={}", registryCodes.size());
    int successCount = 0;
    int failureCount = 0;
    List<String> skippedWithoutSurvey = new ArrayList<>();

    for (var registryCode : registryCodes) {
      if (!latestKybSurveyInputs.hasSurvey(registryCode)) {
        skippedWithoutSurvey.add(registryCode);
        continue;
      }
      try {
        legalEntityScreener.screenLatest(registryCode);
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.error("KYB monitoring failed: registryCode={}", registryCode, e);
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
