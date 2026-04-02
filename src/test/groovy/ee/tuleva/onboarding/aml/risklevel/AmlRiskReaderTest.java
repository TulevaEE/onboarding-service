package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AmlRiskReaderTest {

  private AmlRiskMetadataRepository repository;
  private JdbcClient jdbcClient;
  private JdbcClient.StatementSpec statementSpec;
  private AmlRiskReader reader;

  private static final int HIGH_RISK_LEVEL = 1;
  private static final int MEDIUM_RISK_LEVEL = 2;

  @BeforeEach
  void setUp() {
    repository = mock(AmlRiskMetadataRepository.class);
    jdbcClient = mock(JdbcClient.class);
    statementSpec = mock(JdbcClient.StatementSpec.class);
    when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
    when(statementSpec.update()).thenReturn(0);
    reader = new AmlRiskReader(repository, jdbcClient);
  }

  @Test
  void getHighRiskRows_noResults() {
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of());

    assertThat(reader.getHighRiskRows()).isEmpty();
  }

  @Test
  void getHighRiskRows_mapsMetadata() {
    var row = new AmlRiskMetadata("somePersonalId", HIGH_RISK_LEVEL, Map.of("attr", 5));
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of(row));

    var results = reader.getHighRiskRows();

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getPersonalId()).isEqualTo("somePersonalId");
    assertThat(results.getFirst().getRiskLevel()).isEqualTo(HIGH_RISK_LEVEL);
    assertThat(results.getFirst().getMetadata()).containsEntry("attr", 5);
  }

  @Test
  void getHighRiskRows_nullMetadataBecomesEmptyMap() {
    var row = new AmlRiskMetadata("pid", HIGH_RISK_LEVEL, null);
    when(repository.findAllByRiskLevel(HIGH_RISK_LEVEL)).thenReturn(List.of(row));

    assertThat(reader.getHighRiskRows().getFirst().getMetadata()).isEmpty();
  }

  @Test
  void getMediumRiskRowsSample_emptyFromRepo() {
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of());

    assertThat(reader.getMediumRiskRowsSample(0.01)).isEmpty();
  }

  @Test
  void getMediumRiskRowsSample_zeroOrNegativeProbability() {
    var row = new AmlRiskMetadata("pid", MEDIUM_RISK_LEVEL, Map.of());
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of(row));

    assertThat(reader.getMediumRiskRowsSample(0.0)).isEmpty();
    assertThat(reader.getMediumRiskRowsSample(-0.1)).isEmpty();
  }

  @Test
  void getMediumRiskRowsSample_probabilityOneSelectsAll() {
    var rows =
        IntStream.range(0, 5)
            .mapToObj(i -> new AmlRiskMetadata("pid" + i, MEDIUM_RISK_LEVEL, Map.of("id", i)))
            .toList();
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(rows);

    var results = reader.getMediumRiskRowsSample(1.0);

    assertThat(results).hasSize(5);
    assertThat(results)
        .extracting(RiskLevelResult::getPersonalId)
        .containsExactly("pid0", "pid1", "pid2", "pid3", "pid4");
  }

  @Test
  void getMediumRiskRowsSample_probabilityAboveOneSelectsAll() {
    var rows =
        IntStream.range(0, 3)
            .mapToObj(i -> new AmlRiskMetadata("pid" + i, MEDIUM_RISK_LEVEL, Map.of()))
            .toList();
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(rows);

    assertThat(reader.getMediumRiskRowsSample(1.5)).hasSize(3);
  }

  @Test
  void getMediumRiskRowsSample_nullMetadataBecomesEmptyMap() {
    var row = new AmlRiskMetadata("pid", MEDIUM_RISK_LEVEL, null);
    when(repository.findAllByRiskLevel(MEDIUM_RISK_LEVEL)).thenReturn(List.of(row));

    var results = reader.getMediumRiskRowsSample(1.0);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().getMetadata()).isEmpty();
  }

  @Test
  void refreshMaterializedView_refreshesBothViews() {
    reader.refreshMaterializedView();

    verify(jdbcClient)
        .sql("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_third_pillar_latest_residency");
    verify(jdbcClient).sql("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_aml_risk_metadata");
  }
}
