package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({TkfRiskRepositoryService.class})
class TkfRiskRepositoryServiceTest {

  @Autowired TkfRiskRepositoryService service;
  @Autowired DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM analytics.v_tkf_risk_metadata");
    }
  }

  @Test
  void returnsEmptyWhenNoHighRiskRows() {
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertThat(results).isEmpty();
  }

  @Test
  void returnsHighRiskRowsWithMetadata() throws Exception {
    insertRow("38501010001", 1, "{\"level\": 1, \"total_points\": 105}");

    List<RiskLevelResult> results = service.getHighRiskRows();

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getPersonalId()).isEqualTo("38501010001");
    assertThat(results.getFirst().getRiskLevel()).isEqualTo(1);
    assertThat(results.getFirst().getMetadata()).containsEntry("level", 1);
  }

  @Test
  void doesNotReturnMediumRiskRowsAsHighRisk() throws Exception {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");

    List<RiskLevelResult> results = service.getHighRiskRows();

    assertThat(results).isEmpty();
  }

  @Test
  void returnsMediumRiskSampleWithFullProbability() throws Exception {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.0);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getRiskLevel()).isEqualTo(2);
  }

  @Test
  void returnsEmptyMediumRiskSampleWithZeroProbability() throws Exception {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(0.0);

    assertThat(results).isEmpty();
  }

  private void insertRow(String personalId, int riskLevel, String metadataJson) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "INSERT INTO analytics.v_tkf_risk_metadata (personal_id, risk_level, metadata) VALUES ('"
              + personalId
              + "', "
              + riskLevel
              + ", '"
              + metadataJson
              + "')");
    }
  }
}
