package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskLevelService {

  private final AmlRiskRepositoryService amlRiskRepositoryService;
  private final AmlCheckRepository amlCheckRepository;
  private final ApplicationEventPublisher eventPublisher;

  public void runRiskLevelCheck() {
    log.info("Running AML risk level checks");
    List<RiskLevelResult> rows = amlRiskRepositoryService.getHighRiskRows();
    log.info("Running AML risk level checks, identified {} rows", rows.size());
    createAmlChecks(rows);
  }

  private void createAmlChecks(List<RiskLevelResult> rows) {
    int totalCount = rows.size();

    int createdCount =
        (int)
            rows.stream()
                .filter(row -> StringUtils.hasText(row.getPersonalId()))
                .map(
                    row ->
                        AmlCheck.builder()
                            .personalCode(row.getPersonalId())
                            .type(AmlCheckType.RISK_LEVEL)
                            .success(false)
                            .metadata(row.getMetadata())
                            .build())
                .filter(this::addCheckIfMissing)
                .count();

    log.info(
        "Ran risk-level check. Total high-risk rows: {}, New AML checks created: {}",
        totalCount,
        createdCount);

    eventPublisher.publishEvent(new AmlRiskLevelJobRunEvent(this, totalCount, createdCount));
  }

  public boolean addCheckIfMissing(AmlCheck amlCheck) {
    Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), AmlCheckType.RISK_LEVEL, oneYearAgo);

    // Compare metadata to avoid duplicates with the same fields
    for (AmlCheck e : existing) {
      if (e.getMetadata().equals(amlCheck.getMetadata())) {
        return false;
      }
    }
    AmlCheck saved = amlCheckRepository.save(amlCheck);
    eventPublisher.publishEvent(new AmlCheckCreatedEvent(this, saved));
    return true;
  }
}
