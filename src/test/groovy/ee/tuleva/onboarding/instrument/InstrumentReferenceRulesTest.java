package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InstrumentReferenceRulesTest {

  private static final List<String> INSTRUMENT_TYPES = List.of("ETF", "FUND");
  private static final List<String> ASSET_CLASSES = List.of("equity", "bond");

  @Autowired private JdbcClient jdbcClient;

  @Test
  void everyInstrumentTypeIsOneWeKnow() {
    var types =
        jdbcClient
            .sql("SELECT DISTINCT instrument_type FROM instrument_reference")
            .query(String.class)
            .list();

    assertThat(types).allSatisfy(type -> assertThat(type).isIn(INSTRUMENT_TYPES));
  }

  @Test
  void everyAssetClassIsOneWeKnow() {
    var assetClasses =
        jdbcClient
            .sql("SELECT DISTINCT asset_class FROM instrument_reference")
            .query(String.class)
            .list();

    assertThat(assetClasses).allSatisfy(assetClass -> assertThat(assetClass).isIn(ASSET_CLASSES));
  }

  @Test
  void everyBenchmarkProxyNamesExactlyOneIndexTarget() {
    var contradictions =
        jdbcClient
            .sql(
                """
                SELECT benchmark_category FROM benchmark_category_proxy
                WHERE (index_proxy_isin IS NULL AND index_series_key IS NULL)
                   OR (index_proxy_isin IS NOT NULL AND index_series_key IS NOT NULL)
                """)
            .query(String.class)
            .list();

    assertThat(contradictions).isEmpty();
  }

  @Test
  void everyBenchmarkCategoryOnAnInstrumentHasAProxy() {
    var orphans =
        jdbcClient
            .sql(
                """
                SELECT ir.isin FROM instrument_reference ir
                WHERE ir.benchmark_category IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM benchmark_category_proxy p
                                  WHERE p.benchmark_category = ir.benchmark_category)
                """)
            .query(String.class)
            .list();

    assertThat(orphans).isEmpty();
  }

  @Test
  void anExistingInstrumentCanBeUpdated() {
    assertThatCode(
            () ->
                jdbcClient
                    .sql(
                        "UPDATE instrument_reference SET seb_position_name = :name WHERE isin = :isin")
                    .param("name", "Renamed in a console")
                    .param("isin", "IE00BFG1TM61")
                    .update())
        .doesNotThrowAnyException();
  }
}
