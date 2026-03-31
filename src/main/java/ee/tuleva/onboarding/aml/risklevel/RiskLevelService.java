package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.stream.Collectors.toList;

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

  private final AmlRiskReader amlRiskReader;
  private final TkfRiskReader tkfRiskReader;
  private final AmlCheckRepository amlCheckRepository;
  private final ApplicationEventPublisher eventPublisher;

  public void runRiskLevelCheck(double mediumRiskIndividualSelectionProbability) {
    scorePillar("III pillar", amlRiskReader, RISK_LEVEL, mediumRiskIndividualSelectionProbability);
  }

  public void runTkfRiskLevelCheck(double mediumRiskIndividualSelectionProbability) {
    scorePillar("TKF", tkfRiskReader, TKF_RISK_LEVEL, mediumRiskIndividualSelectionProbability);
  }

  private void scorePillar(
      String label, RiskReader reader, AmlCheckType checkType, double selectionProbability) {
    log.info("Refreshing {} risk views", label);
    reader.refreshMaterializedView();

    List<RiskLevelResult> highRiskRows = reader.getHighRiskRows();
    List<RiskLevelResult> mediumRiskSamples = reader.getMediumRiskRowsSample(selectionProbability);

    log.info(
        "{}: Identified {} high-risk rows and {} medium-risk samples",
        label,
        highRiskRows.size(),
        mediumRiskSamples.size());

    List<AmlCheck> allChecks =
        Stream.concat(highRiskRows.stream(), mediumRiskSamples.stream())
            .filter(row -> StringUtils.hasText(row.getPersonalId()))
            .map(row -> buildAmlCheck(row, checkType))
            .collect(toList());

    int createdCount = (int) allChecks.stream().filter(this::addCheckIfMissing).count();

    log.info("{} risk-level check done. New AML checks created: {}", label, createdCount);

    eventPublisher.publishEvent(
        new AmlRiskLevelJobRunEvent(
            this, label, highRiskRows.size(), mediumRiskSamples.size(), createdCount));
  }

  private AmlCheck buildAmlCheck(RiskLevelResult row, AmlCheckType checkType) {
    Map<String, Object> metadata = new HashMap<>(row.getMetadata());
    metadata.put("level", row.getRiskLevel());
    return AmlCheck.builder()
        .personalCode(row.getPersonalId())
        .type(checkType)
        .success(false)
        .metadata(metadata)
        .build();
  }

  boolean addCheckIfMissing(AmlCheck amlCheck) {
    Instant cutoff = ClockHolder.sixMonthsAgo();
    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), amlCheck.getType(), cutoff);

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
