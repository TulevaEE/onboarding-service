package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.company.CompanyRepository;
import java.time.Clock;
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
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void screenAllCompanies() {
    var startedAt = clock.instant();
    var companies = companyRepository.findAll();
    log.info("Starting daily KYB monitoring: companyCount={}", companies.size());
    int successCount = 0;
    int failureCount = 0;

    for (var company : companies) {
      try {
        legalEntityScreener.screenLatest(company.getRegistryCode());
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
    eventPublisher.publishEvent(new KybMonitoringCompletedEvent(startedAt));
  }
}
