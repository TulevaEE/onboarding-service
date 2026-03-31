package ee.tuleva.onboarding.aml.risklevel;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TkfRiskRepositoryService {

  private static final int HIGH_RISK_LEVEL = 1;
  private static final int MEDIUM_RISK_LEVEL = 2;

  private final TkfRiskMetadataRepository tkfRiskMetadataRepository;
  private final JdbcClient jdbcClient;

  private RiskLevelResult convertToRiskLevelResult(TkfRiskMetadata tkfRisk) {
    Map<String, Object> metadata = tkfRisk.getMetadata() == null ? Map.of() : tkfRisk.getMetadata();
    return new RiskLevelResult(tkfRisk.getPersonalId(), tkfRisk.getRiskLevel(), metadata);
  }

  public List<RiskLevelResult> getHighRiskRows() {
    return tkfRiskMetadataRepository.findAllByRiskLevel(HIGH_RISK_LEVEL).stream()
        .map(this::convertToRiskLevelResult)
        .toList();
  }

  public List<RiskLevelResult> getMediumRiskRowsSample(double individualSelectionProbability) {
    List<TkfRiskMetadata> mediumRiskList =
        tkfRiskMetadataRepository.findAllByRiskLevel(MEDIUM_RISK_LEVEL);

    if (mediumRiskList.isEmpty() || individualSelectionProbability <= 0) {
      return List.of();
    }

    if (individualSelectionProbability >= 1.0) {
      return mediumRiskList.stream().map(this::convertToRiskLevelResult).toList();
    }

    return mediumRiskList.stream()
        .filter(metadata -> Math.random() < individualSelectionProbability)
        .map(this::convertToRiskLevelResult)
        .toList();
  }

  public void refreshMaterializedView() {
    log.info("Start materialized view refresh: analytics.v_tkf_risk_metadata");
    jdbcClient.sql("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_tkf_risk_metadata").update();
    log.info("Materialized view refreshed: analytics.v_tkf_risk_metadata");
  }
}
