package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
      stmt.execute(CREATE_AML_RISK_SHCEMA);
      stmt.execute(CREATE_AML_RISK_VIEW);
      stmt.execute(CREATE_AML_RISK_METADATA_VIEW);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_AML_RISK);
    }
    amlCheckRepository.deleteAll();
  }

  @Test
  @DisplayName("Should create a new AML check for high-risk rows")
  void testRiskLevelCheck_withHighRiskRows() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(AmlRiskTestDataFixtures.INSERT_PERSON_4_2_RISK_LEVEL_1);
      stmt.execute(AmlRiskTestDataFixtures.INSERT_PERSON_BLANK_RISK_LEVEL_1);
      stmt.execute(AmlRiskTestDataFixtures.INSERT_PERSON_5_RISK_LEVEL_2);
    }

    riskLevelService.runRiskLevelCheck();

    List<AmlCheck> checks = amlCheckRepository.findAll();
    assertEquals(1, checks.size());

    AmlCheck check = checks.get(0);
    assertEquals(PERSON_ID_4, check.getPersonalCode());
    assertEquals(false, check.isSuccess());
    assertEquals(AmlCheckType.RISK_LEVEL, check.getType());

    Map<String, Object> metadata = check.getMetadata();
    assertEquals(3, metadata.get("attribute_1"));
    assertEquals(2, metadata.get("attribute_2"));
    assertEquals(1, metadata.get("risk_level"));
  }

  @Test
  @DisplayName("Should not create a new check when existing check has the same metadata")
  void testRiskLevelCheck_existingCheckWithSameMetadata_noNewCheckCreated() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(AmlRiskTestDataFixtures.INSERT_PERSON_4_2_RISK_LEVEL_1);
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
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .build();
    amlCheckRepository.save(existingCheck);

    riskLevelService.runRiskLevelCheck();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(1, checksAfter.size());
  }

  @Test
  @DisplayName("Should not create AML checks when no data in the view")
  void testRiskLevelCheck_noRows_noChecksCreated() {
    riskLevelService.runRiskLevelCheck();
    List<AmlCheck> checks = amlCheckRepository.findAll();
    assertTrue(checks.isEmpty(), "No checks should be created when no data in the view");
  }

  @Test
  @DisplayName("Should create a new AML check even if an existing check is success=true")
  void testRiskLevelCheck_existingCheckButSuccessTrue_newCheckCreated() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(AmlRiskTestDataFixtures.INSERT_PERSON_6_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata = new HashMap<>();
    existingMetadata.put("attribute_1", 5);
    existingMetadata.put("attribute_2", 4);
    existingMetadata.put("risk_level", 1);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_6)
            .type(AmlCheckType.RISK_LEVEL)
            .success(true)
            .metadata(existingMetadata)
            .build();
    amlCheckRepository.save(existingCheck);

    riskLevelService.runRiskLevelCheck();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(2, checksAfter.size());
  }

  @Test
  @DisplayName("Should create a new AML check if the existing one is older than one year")
  void testRiskLevelCheck_existingCheckIsTooOld_newCheckCreated() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(AmlRiskTestDataFixtures.INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata = new HashMap<>();
    existingMetadata.put("attribute_1", 1);
    existingMetadata.put("attribute_2", 1);
    existingMetadata.put("risk_level", 1);

    AmlCheck oldCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(Instant.now().minus(730, ChronoUnit.DAYS))
            .build();
    amlCheckRepository.save(oldCheck);

    riskLevelService.runRiskLevelCheck();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(2, checksAfter.size());
  }
}
