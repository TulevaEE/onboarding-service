package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AmlRiskRepositoryServiceTest {

  private AmlRiskMetadataRepository repository;
  private NamedParameterJdbcTemplate jdbcTemplate;
  private AmlRiskRepositoryService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AmlRiskMetadataRepository.class);
    jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
    service = new AmlRiskRepositoryService(repository, jdbcTemplate);
  }

  @Test
  @DisplayName("Should return an empty list when no high-risk rows are found")
  void getHighRiskRows_noResults() {
    when(repository.findAllByRiskLevel(1)).thenReturn(List.of());
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("Should handle non-null metadata as a Map")
  void getHighRiskRows_mapMetadata() {
    Map<String, Object> testMetadata = Map.of("attribute_1", 5, "attribute_2", 3, "risk_level", 1);
    AmlRiskMetadata row = new AmlRiskMetadata("somePersonalId", 1, testMetadata);

    when(repository.findAllByRiskLevel(1)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());

    RiskLevelResult r = results.get(0);
    assertEquals("somePersonalId", r.getPersonalId());
    assertEquals(1, r.getRiskLevel());
    assertEquals(5, r.getMetadata().get("attribute_1"));
    assertEquals(3, r.getMetadata().get("attribute_2"));
  }

  @Test
  @DisplayName("Should handle null metadata by returning an empty map")
  void getHighRiskRows_nullMetadata() {
    AmlRiskMetadata row = new AmlRiskMetadata("someOtherPersonalId", 1, null);
    when(repository.findAllByRiskLevel(1)).thenReturn(List.of(row));

    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());
    assertTrue(results.get(0).getMetadata().isEmpty());
  }

  @Test
  @DisplayName("Should refresh materialized view")
  void testRefreshMaterializedView() {
    var jdbcOperations = Mockito.mock(org.springframework.jdbc.core.JdbcOperations.class);
    when(jdbcTemplate.getJdbcOperations()).thenReturn(jdbcOperations);

    service.refreshMaterializedView();

    String expectedSql =
        "REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_third_pillar_latest_residency;";
    verify(jdbcOperations).execute(expectedSql);
  }
}
