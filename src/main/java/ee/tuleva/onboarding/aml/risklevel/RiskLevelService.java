package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskLevelService {

  private final JdbcClient jdbcClient;
  private final AmlCheckRepository amlCheckRepository;

  @Transactional
  public void runRiskLevelCheck() {
    String sql =
        "SELECT * "
            + "FROM analytics.v_aml_risk "
            + "WHERE risk_level = 1 "
            + "ORDER BY risk_level DESC";

    List<RiskLevelResult> rows = collectRiskLevelResults(sql);
    createAmlChecks(rows);
  }

  private void createAmlChecks(List<RiskLevelResult> rows) {
    int totalCount = 0;
    int createdCount = 0;

    for (RiskLevelResult row : rows) {
      totalCount++;
      String personalId = row.getPersonalId();
      Map<String, Object> rowMetadata = row.getMetadata();

      // Later we should probably validate non-local id codes differently
      if (StringUtils.hasText(personalId)) {
        AmlCheck amlCheck =
            AmlCheck.builder()
                .personalCode(personalId)
                .type(AmlCheckType.RISK_LEVEL)
                .success(false)
                .metadata(rowMetadata)
                .build();

        if (addCheckIfMissing(amlCheck)) {
          createdCount++;
        }
      }
    }

    log.info(
        "Ran risk-level check. " + "Total high-risk rows: {}, " + "New AML checks created: {}",
        totalCount,
        createdCount);
  }

  @NotNull
  private List<RiskLevelResult> collectRiskLevelResults(String sql) {
    return jdbcClient
        .sql(sql)
        .query(
            (rs, rowNum) -> {
              String personalId = rs.getString("personal_id");
              int riskLevel = rs.getInt("risk_level");

              Map<String, Object> metadata = new HashMap<>();
              var metaData = rs.getMetaData();
              int colCount = metaData.getColumnCount();
              for (int i = 1; i <= colCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                if (!"personal_id".equalsIgnoreCase(columnName)) {
                  Object val = rs.getObject(i);
                  metadata.put(columnName, val);
                }
              }

              return new RiskLevelResult(personalId, riskLevel, metadata);
            })
        .list();
  }

  @Transactional
  public boolean addCheckIfMissing(AmlCheck amlCheck) {
    Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);

    var existing =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            amlCheck.getPersonalCode(), AmlCheckType.RISK_LEVEL, oneYearAgo);

    // If any existing check has the exact same metadata, skip
    for (AmlCheck e : existing) {
      if (e.getMetadata().equals(amlCheck.getMetadata())) {
        return false;
      }
    }

    amlCheckRepository.save(amlCheck);
    return true;
  }
}
