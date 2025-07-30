package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AmlRiskRepositoryServiceTest {

  private AmlRiskMetadataRepository repository;
  private NamedParameterJdbcTemplate jdbcTemplate;
  private AmlRiskRepositoryService service;

  private static final int HIGH_RISK_LEVEL = 1;
  private static final int MEDIUM_RISK_LEVEL = 2;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AmlRiskMetadataRepository.class);
    jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
    service = new AmlRiskRepositoryService(repository, jdbcTemplate);
  }

  @Test
  @DisplayName("Should return an empty list when no high-risk rows are found")
  void getHighRiskRows_noResults() {
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of());
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("Should handle non-null metadata as a Map for high-risk rows")
  void getHighRiskRows_mapMetadata() {
    Map<String, Object> testMetadata =
        Map.of("attribute_1", 5, "attribute_2", 3, "risk_level", HIGH_RISK_LEVEL);
    AmlRiskMetadata row = new AmlRiskMetadata("somePersonalId", HIGH_RISK_LEVEL, testMetadata);

    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());

    RiskLevelResult r = results.get(0);
    assertEquals("somePersonalId", r.getPersonalId());
    assertEquals(HIGH_RISK_LEVEL, r.getRiskLevel());
    assertEquals(5, r.getMetadata().get("attribute_1"));
    assertEquals(3, r.getMetadata().get("attribute_2"));
  }

  @Test
  @DisplayName("Should handle null metadata by returning an empty map for high-risk rows")
  void getHighRiskRows_nullMetadata() {
    AmlRiskMetadata row = new AmlRiskMetadata("someOtherPersonalId", HIGH_RISK_LEVEL, null);
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());
    assertTrue(results.get(0).getMetadata().isEmpty());
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should return an empty list if repository returns empty")
  void getMediumRiskRowsSample_emptyFromRepo() {
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(Collections.emptyList());
    List<RiskLevelResult> results = service.getMediumRiskRowsSample(0.01);
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName(
      "getMediumRiskRowsSample should return an empty list if probability is zero or negative")
  void getMediumRiskRowsSample_zeroOrNegativeProbability() {
    AmlRiskMetadata metadata = new AmlRiskMetadata("pid", MEDIUM_RISK_LEVEL, Map.of());
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of(metadata));

    List<RiskLevelResult> resultsZero = service.getMediumRiskRowsSample(0.0);
    assertTrue(resultsZero.isEmpty());

    List<RiskLevelResult> resultsNegative = service.getMediumRiskRowsSample(-0.1);
    assertTrue(resultsNegative.isEmpty());
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should select all items if probability is 1.0")
  void getMediumRiskRowsSample_probabilityOne_selectAll() {
    List<AmlRiskMetadata> mediumRiskList =
        IntStream.range(0, 5)
            .mapToObj(i -> new AmlRiskMetadata("pid" + i, MEDIUM_RISK_LEVEL, Map.of("id", i)))
            .collect(Collectors.toList());
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(mediumRiskList);

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.0);
    assertEquals(5, results.size());
    for (int i = 0; i < 5; i++) {
      assertEquals("pid" + i, results.get(i).getPersonalId());
      assertEquals(MEDIUM_RISK_LEVEL, results.get(i).getRiskLevel());
    }
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should select all items if probability is greater than 1.0")
  void getMediumRiskRowsSample_probabilityGreaterThanOne_selectAll() {
    List<AmlRiskMetadata> mediumRiskList =
        IntStream.range(0, 3)
            .mapToObj(i -> new AmlRiskMetadata("pid_gt" + i, MEDIUM_RISK_LEVEL, Map.of("id_gt", i)))
            .collect(Collectors.toList());
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(mediumRiskList);

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.5);
    assertEquals(3, results.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("pid_gt" + i, results.get(i).getPersonalId());
      assertEquals(MEDIUM_RISK_LEVEL, results.get(i).getRiskLevel());
    }
  }

  @Test
  @DisplayName("getMediumRiskRowsSample maps data correctly for probabilistically selected items")
  void getMediumRiskRowsSample_mapsDataCorrectly() {
    List<AmlRiskMetadata> mediumRiskList =
        IntStream.range(0, 10)
            .mapToObj(
                i ->
                    new AmlRiskMetadata(
                        "pid" + i,
                        MEDIUM_RISK_LEVEL,
                        Map.of("id", i, "original_risk", MEDIUM_RISK_LEVEL)))
            .collect(Collectors.toList());
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(mediumRiskList);

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(0.5);

    assertTrue(results.size() <= mediumRiskList.size());
    for (RiskLevelResult result : results) {
      assertEquals(MEDIUM_RISK_LEVEL, result.getRiskLevel());
      assertTrue(result.getPersonalId().startsWith("pid"));
      assertTrue(result.getMetadata().containsKey("id"));
      assertEquals(MEDIUM_RISK_LEVEL, result.getMetadata().get("original_risk"));
      assertTrue(
          mediumRiskList.stream().anyMatch(m -> m.getPersonalId().equals(result.getPersonalId())));
    }
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should handle null metadata by returning an empty map")
  void getMediumRiskRowsSample_nullMetadata() {
    AmlRiskMetadata row = new AmlRiskMetadata("mediumRiskId", MEDIUM_RISK_LEVEL, null);
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.0);
    assertEquals(1, results.size());
    RiskLevelResult r = results.get(0);
    assertEquals("mediumRiskId", r.getPersonalId());
    assertEquals(MEDIUM_RISK_LEVEL, r.getRiskLevel());
    assertTrue(r.getMetadata().isEmpty());
  }

  @Test
  @DisplayName("Should refresh materialized views")
  void testRefreshMaterializedView() {
    var jdbcOperations = Mockito.mock(org.springframework.jdbc.core.JdbcOperations.class);
    when(jdbcTemplate.getJdbcOperations()).thenReturn(jdbcOperations);

    service.refreshMaterializedView();

    String expectedThirdPillarRefresh =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_third_pillar_latest_residency;";
    String expectedRiskMetadataRefresh =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata;";

    verify(jdbcOperations).execute(expectedThirdPillarRefresh);
    verify(jdbcOperations).execute(expectedRiskMetadataRefresh);
  }

  @Test
  @DisplayName("Should refresh only AML risk metadata view")
  void testRefreshAmlRiskMetadataView() {
    var jdbcOperations = Mockito.mock(org.springframework.jdbc.core.JdbcOperations.class);
    when(jdbcTemplate.getJdbcOperations()).thenReturn(jdbcOperations);

    service.refreshAmlRiskMetadataView();

    String expectedRiskMetadataRefresh =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata;";

    verify(jdbcOperations).execute(expectedRiskMetadataRefresh);
  }
}
