package ee.tuleva.onboarding.aml.risklevel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmlRiskRepositoryService {

  private static final int HIGH_RISK_LEVEL = 1;
  private static final int MEDIUM_RISK_LEVEL = 2;

  private final AmlRiskMetadataRepository amlRiskMetadataRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  private RiskLevelResult convertToRiskLevelResult(AmlRiskMetadata amlRisk) {
    Map<String, Object> metadata = amlRisk.getMetadata() == null ? Map.of() : amlRisk.getMetadata();
    return new RiskLevelResult(amlRisk.getPersonalId(), amlRisk.getRiskLevel(), metadata);
  }

  public List<RiskLevelResult> getHighRiskRows() {
    return amlRiskMetadataRepository.findAllByRiskLevel(HIGH_RISK_LEVEL).stream()
        .map(this::convertToRiskLevelResult)
        .toList();
  }

  public List<RiskLevelResult> getMediumRiskRowsSample(double individualSelectionProbability) {
    List<AmlRiskMetadata> mediumRiskMetadataList =
        amlRiskMetadataRepository.findAllByRiskLevel(MEDIUM_RISK_LEVEL);

    if (mediumRiskMetadataList.isEmpty() || individualSelectionProbability <= 0) {
      return Collections.emptyList();
    }

    if (individualSelectionProbability >= 1.0) {
      return mediumRiskMetadataList.stream().map(this::convertToRiskLevelResult).toList();
    }

    return mediumRiskMetadataList.stream()
        .filter(metadata -> Math.random() < individualSelectionProbability)
        .map(this::convertToRiskLevelResult)
        .toList();
  }

  public void refreshAmlRiskMetadataView() {
    log.info("Start materialized view refresh: analytics.v_aml_risk_metadata");
    String refreshRiskMetadata =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata;";
    jdbcTemplate.getJdbcOperations().execute(refreshRiskMetadata);
    log.info("Materialized view refreshed: analytics.v_aml_risk_metadata");
  }

  public void refreshMaterializedView() {
    log.info("Start materialized view refresh: analytics.mv_third_pillar_latest_residency");
    String refreshThirdPillarResidencyView =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_third_pillar_latest_residency;";
    jdbcTemplate.getJdbcOperations().execute(refreshThirdPillarResidencyView);
    log.info("Materialized view refreshed: analytics.mv_third_pillar_latest_residency");

    refreshAmlRiskMetadataView();
  }
}
