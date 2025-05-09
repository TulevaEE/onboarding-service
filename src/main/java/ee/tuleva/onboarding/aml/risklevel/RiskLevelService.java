package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

  public void runRiskLevelCheck(double mediumRiskIndividualSelectionProbability) {
    log.info("Refreshing AML risk views");
    amlRiskRepositoryService.refreshMaterializedView();
    log.info("Running AML risk level checks");

    List<RiskLevelResult> highRiskRows = amlRiskRepositoryService.getHighRiskRows();
    List<RiskLevelResult> mediumRiskSamples =
        amlRiskRepositoryService.getMediumRiskRowsSample(mediumRiskIndividualSelectionProbability);

    log.info(
        "Identified {} high-risk rows and {} medium-risk samples (using probability {}) for AML check.",
        highRiskRows.size(),
        mediumRiskSamples.size(),
        String.format("%.8f", mediumRiskIndividualSelectionProbability));

    List<RiskLevelResult> allRowsToProcess = new ArrayList<>(highRiskRows);
    allRowsToProcess.addAll(mediumRiskSamples);

    log.info("Running AML risk level checks, identified {} total rows", allRowsToProcess.size());
    createAmlChecks(allRowsToProcess);
  }

  private void createAmlChecks(List<RiskLevelResult> rows) {
    int totalRowsForProcessing = rows.size();

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
        "Ran risk-level check. Total rows processed: {}, New AML checks created: {}",
        totalRowsForProcessing,
        createdCount);

    eventPublisher.publishEvent(
        new AmlRiskLevelJobRunEvent(this, totalRowsForProcessing, createdCount));
  }

  public boolean addCheckIfMissing(AmlCheck amlCheck) {
    Instant cutoff = ClockHolder.sixMonthsAgo();
    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), AmlCheckType.RISK_LEVEL, cutoff);

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
