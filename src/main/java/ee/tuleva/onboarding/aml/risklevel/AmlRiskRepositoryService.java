package ee.tuleva.onboarding.aml.risklevel;

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

  private final AmlRiskMetadataRepository amlRiskMetadataRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public List<RiskLevelResult> getHighRiskRows() {
    return amlRiskMetadataRepository.findAllByRiskLevel(1).stream()
        .map(
            amlRisk -> {
              Map<String, Object> metadata =
                  amlRisk.getMetadata() == null ? Map.of() : amlRisk.getMetadata();
              return new RiskLevelResult(amlRisk.getPersonalId(), amlRisk.getRiskLevel(), metadata);
            })
        .toList();
  }

  public void refreshMaterializedView() {
    log.info("Start materialized view refresh: analytics.mv_third_pillar_latest_residency");
    String sql =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_third_pillar_latest_residency;";
    jdbcTemplate.getJdbcOperations().execute(sql);
    log.info("Materialized view refreshed: analytics.mv_third_pillar_latest_residency");
  }
}
