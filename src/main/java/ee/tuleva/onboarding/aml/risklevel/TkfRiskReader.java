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
class TkfRiskReader implements RiskReader {

  private static final int HIGH_RISK_LEVEL = 1;
  private static final int MEDIUM_RISK_LEVEL = 2;

  private final TkfRiskMetadataRepository tkfRiskMetadataRepository;
  private final JdbcClient jdbcClient;

  public List<RiskLevelResult> getHighRiskRows() {
    return tkfRiskMetadataRepository.findAllByRiskLevel(HIGH_RISK_LEVEL).stream()
        .map(this::toResult)
        .toList();
  }

  public List<RiskLevelResult> getMediumRiskRowsSample(double individualSelectionProbability) {
    List<TkfRiskMetadata> rows = tkfRiskMetadataRepository.findAllByRiskLevel(MEDIUM_RISK_LEVEL);

    if (rows.isEmpty() || individualSelectionProbability <= 0) {
      return List.of();
    }

    if (individualSelectionProbability >= 1.0) {
      return rows.stream().map(this::toResult).toList();
    }

    return rows.stream()
        .filter(row -> Math.random() < individualSelectionProbability)
        .map(this::toResult)
        .toList();
  }

  public void refreshMaterializedView() {
    log.info("Start materialized view refresh: analytics.v_tkf_risk_metadata");
    jdbcClient.sql("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_tkf_risk_metadata").update();
    log.info("Materialized view refreshed: analytics.v_tkf_risk_metadata");
  }

  private RiskLevelResult toResult(TkfRiskMetadata row) {
    Map<String, Object> metadata = row.getMetadata() == null ? Map.of() : row.getMetadata();
    return new RiskLevelResult(row.getPersonalId(), row.getRiskLevel(), metadata);
  }
}
