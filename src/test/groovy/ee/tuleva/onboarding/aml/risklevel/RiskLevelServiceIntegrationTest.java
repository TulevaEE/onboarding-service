package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.*;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
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
import java.util.Collections;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class RiskLevelServiceIntegrationTest {

  @Autowired DataSource dataSource;
  @Autowired AmlCheckRepository amlCheckRepository;
  @Autowired RiskLevelService riskLevelService;
  @MockitoSpyBean AmlRiskRepositoryService amlRiskRepositoryService;

  private static final double MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY = 0.025;
  private static final double DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN = 30.0;
  private static final double PROBABILITY_FOR_DAILY_RUN =
      MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY / DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN;

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
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_2_RISK_LEVEL_1);
      stmt.execute(INSERT_PERSON_BLANK_RISK_LEVEL_1);
      stmt.execute(INSERT_PERSON_5_RISK_LEVEL_2);
    }

    doReturn(Collections.emptyList())
        .when(amlRiskRepositoryService)
        .getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checks = amlCheckRepository.findAll();
    assertEquals(1, checks.size());

    AmlCheck check = checks.get(0);
    assertEquals(PERSON_ID_4, check.getPersonalCode());
    assertEquals(false, check.isSuccess());
    assertEquals(AmlCheckType.RISK_LEVEL, check.getType());

    Map<String, Object> metadata = check.getMetadata();
    assertEquals("2.0", metadata.get("version"));
    assertEquals(1, metadata.get("level"));
    assertEquals(3, metadata.get("attribute_1"));
    assertEquals(2, metadata.get("attribute_2"));
  }

  @Test
  @DisplayName("Should create an AML check if a medium-risk row is sampled")
  void testRiskLevelCheck_withMediumRiskRowSampled() throws Exception {
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_5_RISK_LEVEL_2);
    }

    Map<String, Object> person5Metadata =
        Map.of(
            "version", "2.0",
            "level", 2,
            "attribute_1", 4,
            "attribute_2", 0,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0);
    RiskLevelResult sampledMediumRiskPerson = new RiskLevelResult(PERSON_ID_5, 2, person5Metadata);

    doReturn(Collections.emptyList()).when(amlRiskRepositoryService).getHighRiskRows();
    doReturn(List.of(sampledMediumRiskPerson))
        .when(amlRiskRepositoryService)
        .getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            PERSON_ID_5, TestClockHolder.now.minus(100, ChronoUnit.DAYS));
    assertEquals(1, checks.size());
    AmlCheck check = checks.get(0);
    assertEquals(PERSON_ID_5, check.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, check.getType());
    assertEquals(false, check.isSuccess());
    assertEquals(person5Metadata, check.getMetadata());
  }

  @Test
  @DisplayName("Should not create a new check when existing check has the same metadata")
  void testRiskLevelCheck_existingCheckWithSameMetadata_noNewCheckCreated() throws Exception {
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "version", "2.0",
            "level", 1,
            "attribute_1", 3,
            "attribute_2", 2,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(100, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(existingCheck);

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(1, checksAfter.size(), "No new check should be created");
  }

  @Test
  @DisplayName("Should not create AML checks when no data in the view")
  void testRiskLevelCheck_noRows_noChecksCreated() {
    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checks = amlCheckRepository.findAll();
    assertTrue(checks.isEmpty(), "No checks should be created when no data in the view");
  }

  @Test
  @DisplayName("Should create a new AML check even if an existing check is success=true")
  void testRiskLevelCheck_existingCheckButSuccessTrue_newCheckCreated() throws Exception {
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_6_RISK_LEVEL_1);
    }

    Map<String, Object> existingCheckMetadata =
        Map.of(
            "version", "2.0",
            "level", 1,
            "attribute_1", 5,
            "attribute_2", 4,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_6)
            .type(AmlCheckType.RISK_LEVEL)
            .success(true)
            .metadata(existingCheckMetadata)
            .createdTime(TestClockHolder.now.minus(60, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(existingCheck);

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(
        2,
        checksAfter.size(),
        "We should have one old (success=true) + one new (success=false) check");

    Map<String, Object> expectedNewCheckMetadata =
        Map.of(
            "version", "2.0",
            "level", 1,
            "attribute_1", 5,
            "attribute_2", 4,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0);

    assertTrue(
        checksAfter.stream()
            .anyMatch(
                c ->
                    c.getPersonalCode().equals(PERSON_ID_6)
                        && !c.isSuccess()
                        && c.getMetadata().equals(expectedNewCheckMetadata)));
  }

  @Test
  @DisplayName("Should create a new AML check if the existing one is older than six months")
  void testRiskLevelCheck_existingCheckIsTooOld_newCheckCreated() throws Exception {
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata = Map.of("attribute_1", 1, "attribute_2", 1, "level", 1);

    AmlCheck oldCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(190, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(oldCheck);

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(2, checksAfter.size(), "One old + one new check expected");
  }

  @Test
  @DisplayName("Should skip check if existing check with same metadata is within six months")
  void testRiskLevelCheck_existingCheckSameMetadataWithinSixMonths_noNewCheckCreated()
      throws Exception {
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "version", "2.0",
            "level", 1,
            "attribute_1", 3,
            "attribute_2", 2,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0);

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode(PERSON_ID_4)
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(existingMetadata)
            .createdTime(TestClockHolder.now.minus(100, ChronoUnit.DAYS))
            .build();

    amlCheckRepository.save(existingCheck);

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(1, checksAfter.size(), "No new check should be created");
  }

  @Test
  @DisplayName("Should create check if existing check with same metadata is older than six months")
  void testRiskLevelCheck_existingCheckSameMetadataOlderThanSixMonths_newCheckCreated()
      throws Exception {
    // given
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_4_RISK_LEVEL_1);
    }

    Map<String, Object> existingMetadata =
        Map.of(
            "version", "2.0",
            "level", 1,
            "attribute_1", 3,
            "attribute_2", 2,
            "attribute_3", 0,
            "attribute_4", 0,
            "attribute_5", 0);

    Clock originalClock = ClockHolder.clock();
    try {
      Clock pastClock = Clock.fixed(originalClock.instant().minus(190, ChronoUnit.DAYS), UTC);
      ClockHolder.setClock(pastClock);

      AmlCheck existingCheck =
          AmlCheck.builder()
              .personalCode(PERSON_ID_4)
              .type(AmlCheckType.RISK_LEVEL)
              .success(false)
              .metadata(existingMetadata)
              .build();
      amlCheckRepository.saveAndFlush(existingCheck);
    } finally {
      ClockHolder.setClock(originalClock);
    }

    // when
    riskLevelService.runRiskLevelCheck(PROBABILITY_FOR_DAILY_RUN);

    // then
    InOrder inOrder = inOrder(amlRiskRepositoryService);
    inOrder.verify(amlRiskRepositoryService).refreshMaterializedView();
    inOrder.verify(amlRiskRepositoryService).getHighRiskRows();
    inOrder.verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(PROBABILITY_FOR_DAILY_RUN));

    List<AmlCheck> checksAfter = amlCheckRepository.findAll();
    assertEquals(
        2, checksAfter.size(), "Should have old + new check for older-than-six-months scenario");
  }
}
