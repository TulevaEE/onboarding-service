package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.*;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class RiskLevelServiceIntegrationTest {

  @Autowired DataSource dataSource;
  @Autowired AmlCheckRepository amlCheckRepository;
  @Autowired RiskLevelService riskLevelService;
  @MockitoSpyBean AmlRiskRepositoryService amlRiskRepositoryService;

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
  void setUp() throws Exception {
    // Prevent the Postgres-specific statement from running on H2
    doNothing().when(amlRiskRepositoryService).refreshMaterializedView();

    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_AML_RISK);
    }
    amlCheckRepository.deleteAll();
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("Should create a new AML check for high-risk rows")
  void testRiskLevelCheck_withHighRiskRows() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_2_RISK_LEVEL_1);
      stmt.execute(INSERT_PERSON_BLANK_RISK_LEVEL_1);
      stmt.execute(INSERT_PERSON_5_RISK_LEVEL_2);
    }

    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

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
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "attribute_1", 3,
            "attribute_2", 2,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0,
            "risk_level", 1);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(100, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(existingCheck);

    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(1, checksAfter.size(), "No new check should be created");
  }

  @Test
  @DisplayName("Should not create AML checks when no data in the view")
  void testRiskLevelCheck_noRows_noChecksCreated() {
    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

    List<AmlCheck> checks = amlCheckRepository.findAll();
    assertTrue(checks.isEmpty(), "No checks should be created when no data in the view");
  }

  @Test
  @DisplayName("Should create a new AML check even if an existing check is success=true")
  void testRiskLevelCheck_existingCheckButSuccessTrue_newCheckCreated() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_6_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "attribute_1", 5,
            "attribute_2", 4,
            "risk_level", 1);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_6)
            .type(AmlCheckType.RISK_LEVEL)
            .success(true)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(60, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(existingCheck);

    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(2, checksAfter.size(), "We should have one old + one new check");
  }

  @Test
  @DisplayName("Should create a new AML check if the existing one is older than one year")
  void testRiskLevelCheck_existingCheckIsTooOld_newCheckCreated() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "attribute_1", 1,
            "attribute_2", 1,
            "risk_level", 1);

    AmlCheck oldCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(730, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(oldCheck);

    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(2, checksAfter.size(), "One old + one new check expected");
  }

  @Test
  @DisplayName("Should skip check if existing check with same metadata is within six months")
  void testRiskLevelCheck_existingCheckSameMetadataWithinSixMonths_noNewCheckCreated()
      throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "attribute_1", 3,
            "attribute_2", 2,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0,
            "risk_level", 1);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(100, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(existingCheck);

    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(1, checksAfter.size(), "No new check should be created");
  }

  @Test
  @DisplayName("Should create check if existing check with same metadata is older than six months")
  void testRiskLevelCheck_existingCheckSameMetadataOlderThanSixMonths_newCheckCreated()
      throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "attribute_1", 3,
            "attribute_2", 2,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0,
            "risk_level", 1);

    var currentClock = ClockHolder.clock();
    ClockHolder.setClock(Clock.fixed(currentClock.instant().minus(190, ChronoUnit.DAYS), UTC));
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .build();

    amlCheckRepository.save(existingCheck);
    // restore test clock to "now"
    ClockHolder.setClock(TestClockHolder.clock);

    riskLevelService.runRiskLevelCheck();

    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(2, checksAfter.size(), "Should have old + new check for older-than-six-months");
  }
}
