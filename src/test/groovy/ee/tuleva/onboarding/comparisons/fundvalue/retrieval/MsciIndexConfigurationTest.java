package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class MsciIndexConfigurationTest {

  private final MsciIndexConfiguration configuration = new MsciIndexConfiguration();
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void theDevelopedWorldIndexFundsScreenedIndexIsStoredUnderItsOwnKey() {
    var restClientBuilder = RestClient.builder();
    var server = expectIndexCode(restClientBuilder, "721415");

    var retriever =
        configuration.msciWorldScreenedIndexRetriever(
            restClientBuilder, jsonMapper, Clock.systemUTC());
    var values =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24));

    assertThat(retriever.getKey()).isEqualTo("MSCI_WORLD_SCREENED");
    assertThat(values)
        .singleElement()
        .satisfies(value -> assertThat(value.key()).isEqualTo("MSCI_WORLD_SCREENED"));
    server.verify();
  }

  @Test
  void theEmergingMarketsIndexFundsScreenedIndexIsStoredUnderItsOwnKey() {
    var restClientBuilder = RestClient.builder();
    var server = expectIndexCode(restClientBuilder, "726578");

    var retriever =
        configuration.msciEmExSelectControversiesIndexRetriever(
            restClientBuilder, jsonMapper, Clock.systemUTC());
    var values =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24));

    assertThat(retriever.getKey()).isEqualTo("MSCI_EM_EX_SELECT_CONTROVERSIES");
    assertThat(values)
        .singleElement()
        .satisfies(value -> assertThat(value.key()).isEqualTo("MSCI_EM_EX_SELECT_CONTROVERSIES"));
    server.verify();
  }

  private static MockRestServiceServer expectIndexCode(
      RestClient.Builder restClientBuilder, String indexCode) {
    var server = MockRestServiceServer.bindTo(restClientBuilder).build();
    server
        .expect(
            requestTo(
                "https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph"
                    + "?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR"
                    + "&start_date=20261124&end_date=20261124&data_frequency=DAILY"
                    + "&baseValue=false&index_codes="
                    + indexCode))
        .andRespond(
            withSuccess(
                """
                {"indexes": {"INDEX_LEVELS": [{"level_eod": 5933.2189, "calc_date": "20261124"}]}}
                """,
                MediaType.APPLICATION_JSON));
    return server;
  }
}
