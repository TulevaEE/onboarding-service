package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureJdbc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureJdbc
@Import(TkfRiskReader.class)
class TkfRiskReaderTest {

  @Autowired TkfRiskReader service;

  @Autowired JdbcClient jdbcClient;

  @Test
  void returnsEmptyWhenNoHighRiskRows() {
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertThat(results).isEmpty();
  }

  @Test
  void returnsHighRiskRowsWithMetadata() {
    insertRow("38501010001", 1, "{\"level\": 1, \"total_points\": 105}");

    List<RiskLevelResult> results = service.getHighRiskRows();

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getPersonalId()).isEqualTo("38501010001");
    assertThat(results.getFirst().getRiskLevel()).isEqualTo(1);
    assertThat(results.getFirst().getMetadata()).containsEntry("level", 1);
  }

  @Test
  void doesNotReturnMediumRiskRowsAsHighRisk() {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");

    List<RiskLevelResult> results = service.getHighRiskRows();

    assertThat(results).isEmpty();
  }

  @Test
  void returnsMediumRiskSampleWithFullProbability() {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.0);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getRiskLevel()).isEqualTo(2);
  }

  @Test
  void returnsEmptyMediumRiskSampleWithZeroProbability() {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(0.0);

    assertThat(results).isEmpty();
  }

  @Test
  void handlesNullMetadata() {
    jdbcClient
        .sql(
            "INSERT INTO analytics.v_tkf_risk_metadata (personal_id, risk_level, metadata) VALUES (?, ?, NULL)")
        .param("38501010001")
        .param(1)
        .update();

    List<RiskLevelResult> results = service.getHighRiskRows();

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getMetadata()).isEmpty();
  }

  @Test
  void returnsMediumRiskSampleWithProbabilityAboveOne() {
    insertRow("39001010002", 2, "{\"level\": 2, \"total_points\": 20}");
    insertRow("39001010003", 2, "{\"level\": 2, \"total_points\": 30}");

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.5);

    assertThat(results).hasSize(2);
  }

  private void insertRow(String personalId, int riskLevel, String metadataJson) {
    jdbcClient
        .sql(
            "INSERT INTO analytics.v_tkf_risk_metadata (personal_id, risk_level, metadata) VALUES (?, ?, ?)")
        .param(personalId)
        .param(riskLevel)
        .param(metadataJson)
        .update();
  }
}
