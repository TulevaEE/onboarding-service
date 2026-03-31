package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final TkfRiskRepositoryService tkfRiskRepositoryService;
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

    List<AmlCheck> allChecks = Stream.concat(highRiskChecks, mediumRiskChecks).collect(toList());

    int highRiskCount = highRiskRows.size();
    int mediumRiskCount = mediumRiskSamples.size();
    int totalRowsForProcessing = highRiskCount + mediumRiskCount;
    log.info(
        "Running AML risk level checks, identified {} total rows for processing",
        totalRowsForProcessing);

    int createdCount = (int) allChecks.stream().filter(this::addCheckIfMissing).count();

    runTkfRiskLevelCheck(mediumRiskIndividualSelectionProbability);

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
        .type(RISK_LEVEL)
        .success(false)
        .metadata(metadata)
        .build();
  }

  public boolean addCheckIfMissing(AmlCheck amlCheck) {
    Instant cutoff = ClockHolder.sixMonthsAgo();
    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), RISK_LEVEL, cutoff);

    for (AmlCheck e : existing) {
      if (metadataEqualsIgnoringVersion(e.getMetadata(), amlCheck.getMetadata())) {
        return false;
      }
    }
    AmlCheck saved = amlCheckRepository.save(amlCheck);
    eventPublisher.publishEvent(new AmlCheckCreatedEvent(this, saved));
    return true;
  }

  private void runTkfRiskLevelCheck(double mediumRiskIndividualSelectionProbability) {
    log.info("Refreshing TKF risk views");
    tkfRiskRepositoryService.refreshMaterializedView();
    log.info("Running TKF risk level checks");

    List<RiskLevelResult> highRiskRows = tkfRiskRepositoryService.getHighRiskRows();
    List<RiskLevelResult> mediumRiskSamples =
        tkfRiskRepositoryService.getMediumRiskRowsSample(mediumRiskIndividualSelectionProbability);

    log.info(
        "TKF: Identified {} high-risk rows and {} medium-risk samples",
        highRiskRows.size(),
        mediumRiskSamples.size());

    Stream<AmlCheck> highRiskChecks =
        highRiskRows.stream()
            .filter(row -> StringUtils.hasText(row.getPersonalId()))
            .map(this::buildTkfAmlCheck);

    Stream<AmlCheck> mediumRiskChecks =
        mediumRiskSamples.stream()
            .filter(row -> StringUtils.hasText(row.getPersonalId()))
            .map(this::buildTkfAmlCheck);

    List<AmlCheck> allTkfChecks = Stream.concat(highRiskChecks, mediumRiskChecks).collect(toList());

    int createdCount = (int) allTkfChecks.stream().filter(this::addTkfCheckIfMissing).count();

    log.info("TKF risk-level check done. New AML checks created: {}", createdCount);
  }

  private AmlCheck buildTkfAmlCheck(RiskLevelResult row) {
    Map<String, Object> metadata = new HashMap<>(row.getMetadata());
    return AmlCheck.builder()
        .personalCode(row.getPersonalId())
        .type(TKF_RISK_LEVEL)
        .success(false)
        .metadata(metadata)
        .build();
  }

  private boolean addTkfCheckIfMissing(AmlCheck amlCheck) {
    Instant cutoff = ClockHolder.sixMonthsAgo();
    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), TKF_RISK_LEVEL, cutoff);

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
