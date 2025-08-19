package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    Stream<AmlCheck> highRiskChecks =
        highRiskRows.stream()
            .filter(row -> StringUtils.hasText(row.getPersonalId()))
            .map(row -> buildAmlCheck(row, 1));

    Stream<AmlCheck> mediumRiskChecks =
        mediumRiskSamples.stream()
            .filter(row -> StringUtils.hasText(row.getPersonalId()))
            .map(row -> buildAmlCheck(row, 2));

    List<AmlCheck> allChecks =
        Stream.concat(highRiskChecks, mediumRiskChecks).collect(Collectors.toList());

    int highRiskCount = highRiskRows.size();
    int mediumRiskCount = mediumRiskSamples.size();
    int totalRowsForProcessing = highRiskCount + mediumRiskCount;
    log.info(
        "Running AML risk level checks, identified {} total rows for processing",
        totalRowsForProcessing);

    int createdCount = (int) allChecks.stream().filter(this::addCheckIfMissing).count();

    log.info(
        "Ran risk-level check. Total rows processed: {}, New AML checks created: {}",
        totalRowsForProcessing,
        createdCount);

    eventPublisher.publishEvent(
        new AmlRiskLevelJobRunEvent(this, highRiskCount, mediumRiskCount, createdCount));
  }

  private AmlCheck buildAmlCheck(RiskLevelResult row, Integer level) {
    Map<String, Object> metadata = new HashMap<>(row.getMetadata());
    metadata.put("level", level);
    return AmlCheck.builder()
        .personalCode(row.getPersonalId())
        .type(AmlCheckType.RISK_LEVEL)
        .success(false)
        .metadata(metadata)
        .build();
  }

  public boolean addCheckIfMissing(AmlCheck amlCheck) {
    Instant cutoff = ClockHolder.sixMonthsAgo();
    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), AmlCheckType.RISK_LEVEL, cutoff);

    for (AmlCheck e : existing) {
      if (metadataEqualsIgnoringVersion(e.getMetadata(), amlCheck.getMetadata())) {
        return false;
      }
    }
    AmlCheck saved = amlCheckRepository.save(amlCheck);
    eventPublisher.publishEvent(new AmlCheckCreatedEvent(this, saved));
    return true;
  }

  private boolean metadataEqualsIgnoringVersion(Map<String, Object> m1, Map<String, Object> m2) {
    if (m1 == m2) {
      return true;
    }
    if (m1 == null || m2 == null) {
      return false;
    }

    Map<String, Object> m1Copy = new HashMap<>(m1);
    Map<String, Object> m2Copy = new HashMap<>(m2);

    m1Copy.remove("version");
    m2Copy.remove("version");

    return m1Copy.equals(m2Copy);
  }
}
