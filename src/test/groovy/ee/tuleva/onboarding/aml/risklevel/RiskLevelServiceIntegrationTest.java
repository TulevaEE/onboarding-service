package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RiskLevelServiceIntegrationTest {

  @Autowired DataSource dataSource;

  @Autowired AmlCheckRepository amlCheckRepository;

  @Autowired RiskLevelService riskLevelService;

  @BeforeAll
  static void setupH2Schema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS analytics");

      stmt.execute(
          """
            CREATE TABLE analytics.v_aml_risk (
              personal_id VARCHAR(50),
              attribute_1 INT,
              attribute_2 INT,
              attribute_3 INT,
              attribute_4 INT,
              attribute_5 INT,
              risk_level INT
            )
          """);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE analytics.v_aml_risk");
    }
    amlCheckRepository.deleteAll();
  }

  @Test
  void testRiskLevelCheck_withHighRiskRows() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          """
            INSERT INTO analytics.v_aml_risk (
              personal_id, attribute_1, attribute_2,
              attribute_3, attribute_4, risk_level, attribute_5
            )
            VALUES (
              '37605030299',
              3, 2, 0, 0, 1, 0
            )
        """);

      // empty id will be skipped
      stmt.execute(
          """
            INSERT INTO analytics.v_aml_risk (
              personal_id, attribute_1, attribute_2,
              attribute_3, attribute_4, risk_level, attribute_5
            )
            VALUES (
              '           ',
              5, 1, 0, 0, 1, 0
            )
        """);

      // risk_level=2 => won't be returned by the service query
      stmt.execute(
          """
            INSERT INTO analytics.v_aml_risk (
              personal_id, attribute_1, attribute_2,
              attribute_3, attribute_4, risk_level, attribute_5
            )
            VALUES (
              '38888888889',
              0, 0, 0, 0, 2, 0
            )
        """);
    }

    // Run the service
    riskLevelService.runRiskLevelCheck();

    // Verify results
    List<AmlCheck> checks = amlCheckRepository.findAll();
    assertEquals(1, checks.size());

    AmlCheck check = checks.get(0);
    assertEquals("37605030299", check.getPersonalCode());
    assertEquals(false, check.isSuccess());
    assertEquals(AmlCheckType.RISK_LEVEL, check.getType());
  }

  @Test
  void testRiskLevelCheck_existingCheckWithSameMetadata_noNewCheckCreated() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          """
            INSERT INTO analytics.v_aml_risk (
              personal_id, attribute_1, attribute_2,
              attribute_3, attribute_4, risk_level, attribute_5
            )
            VALUES (
              '37605030299',
              3, 2, 0, 0, 1, 0
            )
        """);
    }

    Map<String, Object> existingMetadata = new HashMap<>();
    existingMetadata.put("attribute_1", 3);
    existingMetadata.put("attribute_2", 2);
    existingMetadata.put("attribute_3", 0);
    existingMetadata.put("attribute_4", 0);
    existingMetadata.put("attribute_5", 0);
    existingMetadata.put("risk_level", 1);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("37605030299")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .build();
    amlCheckRepository.save(existingCheck);

    riskLevelService.runRiskLevelCheck();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    // We expect only the original 1 check
    assertEquals(
        1, checksAfter.size(), "No new check should be created if same metadata already exists");
  }
}
