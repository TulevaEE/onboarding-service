package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class TkfRiskRepositoryServiceTest {

  private TkfRiskMetadataRepository repository;
  private NamedParameterJdbcTemplate jdbcTemplate;
  private TkfRiskRepositoryService service;

  private static final int HIGH_RISK_LEVEL = 1;
  private static final int MEDIUM_RISK_LEVEL = 2;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(TkfRiskMetadataRepository.class);
    jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
    service = new TkfRiskRepositoryService(repository, jdbcTemplate);
  }

  @Test
  @DisplayName("Should return empty list when no high-risk TKF rows found")
  void getHighRiskRows_noResults() {
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of());
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("Should correctly map TKF metadata for high-risk rows")
  void getHighRiskRows_withMetadata() {
    Map<String, Object> testMetadata =
        Map.of(
            "level",
            1,
            "total_points",
            105,
            "rule_1_non_citizen_non_resident",
            100,
            "rule_6_pep_eea",
            5);
    TkfRiskMetadata row = new TkfRiskMetadata("38501010001", HIGH_RISK_LEVEL, testMetadata);

    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());

    RiskLevelResult r = results.get(0);
    assertEquals("38501010001", r.getPersonalId());
    assertEquals(HIGH_RISK_LEVEL, r.getRiskLevel());
    assertEquals(100, r.getMetadata().get("rule_1_non_citizen_non_resident"));
    assertEquals(105, r.getMetadata().get("total_points"));
  }

  @Test
  @DisplayName("Should handle null metadata by returning empty map")
  void getHighRiskRows_nullMetadata() {
    TkfRiskMetadata row = new TkfRiskMetadata("38501010001", HIGH_RISK_LEVEL, null);
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());
    assertTrue(results.get(0).getMetadata().isEmpty());
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should return empty list if repository is empty")
  void getMediumRiskRowsSample_emptyFromRepo() {
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(Collections.emptyList());
    List<RiskLevelResult> results = service.getMediumRiskRowsSample(0.01);
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should return all rows when probability is 1.0")
  void getMediumRiskRowsSample_fullProbability() {
    TkfRiskMetadata row = new TkfRiskMetadata("39001010002", MEDIUM_RISK_LEVEL, Map.of("level", 2));
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(1.0);
    assertEquals(1, results.size());
  }

  @Test
  @DisplayName("getMediumRiskRowsSample should return empty when probability is 0")
  void getMediumRiskRowsSample_zeroProbability() {
    TkfRiskMetadata row = new TkfRiskMetadata("39001010002", MEDIUM_RISK_LEVEL, Map.of("level", 2));
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getMediumRiskRowsSample(0.0);
    assertTrue(results.isEmpty());
  }
}
