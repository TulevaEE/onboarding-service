package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.MsciIndexRetriever.PROVIDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MsciIndexRetrieverTest {

  @Test
  void fetchesMsciWorldIndexWithCorrectIndexCodeAndKey() {
    var restClientBuilder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(restClientBuilder).build();
    var retriever = new MsciIndexRetriever("MSCI_WORLD", "990100", restClientBuilder);

    var mockResponse =
        """
        {
          "indexes": {
            "INDEX_LEVELS": [
              {"level_eod": 200.0, "calc_date": "20240102"},
              {"level_eod": 201.5, "calc_date": "20240103"}
            ]
          }
        }
        """;

    server
        .expect(
            requestTo(
                "https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph"
                    + "?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR"
                    + "&start_date=20240102&end_date=20240103&data_frequency=DAILY"
                    + "&baseValue=false&index_codes=990100"))
        .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));

    assertThat(retriever.getKey()).isEqualTo("MSCI_WORLD");
    var expected =
        java.util.List.of(
            aFundValue("MSCI_WORLD", LocalDate.of(2024, 1, 2), 200.0, PROVIDER),
            aFundValue("MSCI_WORLD", LocalDate.of(2024, 1, 3), 201.5, PROVIDER));
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected);
    server.verify();
  }

  @Test
  void fetchesMsciAcwiIndexWithCorrectIndexCodeAndKey() {
    var restClientBuilder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(restClientBuilder).build();
    var retriever = new MsciIndexRetriever("MSCI_ACWI", "892400", restClientBuilder);

    var mockResponse =
        """
        {
          "indexes": {
            "INDEX_LEVELS": [
              {"level_eod": 100.0, "calc_date": "20240102"},
              {"level_eod": 101.5, "calc_date": "20240103"},
              {"level_eod": 102.3, "calc_date": "20240104"}
            ]
          }
        }
        """;

    server
        .expect(
            requestTo(
                "https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph"
                    + "?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR"
                    + "&start_date=20240102&end_date=20240104&data_frequency=DAILY"
                    + "&baseValue=false&index_codes=892400"))
        .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(retriever.getKey()).isEqualTo("MSCI_ACWI");
    var expected =
        java.util.List.of(
            aFundValue("MSCI_ACWI", LocalDate.of(2024, 1, 2), 100.0, PROVIDER),
            aFundValue("MSCI_ACWI", LocalDate.of(2024, 1, 3), 101.5, PROVIDER),
            aFundValue("MSCI_ACWI", LocalDate.of(2024, 1, 4), 102.3, PROVIDER));
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected);
    server.verify();
  }

  @Test
  void fetchesMsciEmIndexWithCorrectIndexCodeAndKey() {
    var restClientBuilder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(restClientBuilder).build();
    var retriever = new MsciIndexRetriever("MSCI_EM", "891800", restClientBuilder);

    var mockResponse =
        """
        {
          "indexes": {
            "INDEX_LEVELS": [
              {"level_eod": 50.0, "calc_date": "20240102"}
            ]
          }
        }
        """;

    server
        .expect(
            requestTo(
                "https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph"
                    + "?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR"
                    + "&start_date=20240102&end_date=20240102&data_frequency=DAILY"
                    + "&baseValue=false&index_codes=891800"))
        .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    assertThat(retriever.getKey()).isEqualTo("MSCI_EM");
    var expected =
        java.util.List.of(aFundValue("MSCI_EM", LocalDate.of(2024, 1, 2), 50.0, PROVIDER));
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected);
    server.verify();
  }
}
