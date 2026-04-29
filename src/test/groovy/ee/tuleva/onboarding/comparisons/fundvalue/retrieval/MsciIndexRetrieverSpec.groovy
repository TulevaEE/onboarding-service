package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.MsciIndexRetriever.PROVIDER
import static org.assertj.core.api.Assertions.assertThat
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class MsciIndexRetrieverSpec extends Specification {

  def "fetches MSCI World index with correct index code and key"() {
    given:
    def restClientBuilder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(restClientBuilder).build()
    def retriever = new MsciIndexRetriever("MSCI_WORLD", "990100", restClientBuilder)

    def mockResponse = """
{
  "indexes": {
    "INDEX_LEVELS": [
      {"level_eod": 200.0, "calc_date": "20240102"},
      {"level_eod": 201.5, "calc_date": "20240103"}
    ]
  }
}
    """

    server.expect(requestTo("https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR&start_date=20240102&end_date=20240103&data_frequency=DAILY&baseValue=false&index_codes=990100"))
      .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON))

    when:
    def result = retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3))

    then:
    retriever.key == "MSCI_WORLD"
    def expected = [
      aFundValue("MSCI_WORLD", LocalDate.of(2024, 1, 2), 200.0, PROVIDER),
      aFundValue("MSCI_WORLD", LocalDate.of(2024, 1, 3), 201.5, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    server.verify()
  }

  def "fetches MSCI ACWI index with correct index code and key"() {
    given:
    def restClientBuilder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(restClientBuilder).build()
    def retriever = new MsciIndexRetriever("MSCI_ACWI", "892400", restClientBuilder)

    def mockResponse = """
{
  "indexes": {
    "INDEX_LEVELS": [
      {"level_eod": 100.0, "calc_date": "20240102"},
      {"level_eod": 101.5, "calc_date": "20240103"},
      {"level_eod": 102.3, "calc_date": "20240104"}
    ]
  }
}
    """

    server.expect(requestTo("https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR&start_date=20240102&end_date=20240104&data_frequency=DAILY&baseValue=false&index_codes=892400"))
      .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON))

    when:
    def result = retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4))

    then:
    retriever.key == "MSCI_ACWI"
    def expected = [
      aFundValue("MSCI_ACWI", LocalDate.of(2024, 1, 2), 100.0, PROVIDER),
      aFundValue("MSCI_ACWI", LocalDate.of(2024, 1, 3), 101.5, PROVIDER),
      aFundValue("MSCI_ACWI", LocalDate.of(2024, 1, 4), 102.3, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    server.verify()
  }

  def "fetches MSCI EM index with correct index code and key"() {
    given:
    def restClientBuilder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(restClientBuilder).build()
    def retriever = new MsciIndexRetriever("MSCI_EM", "891800", restClientBuilder)

    def mockResponse = """
{
  "indexes": {
    "INDEX_LEVELS": [
      {"level_eod": 50.0, "calc_date": "20240102"}
    ]
  }
}
    """

    server.expect(requestTo("https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR&start_date=20240102&end_date=20240102&data_frequency=DAILY&baseValue=false&index_codes=891800"))
      .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON))

    when:
    def result = retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2))

    then:
    retriever.key == "MSCI_EM"
    def expected = [
      aFundValue("MSCI_EM", LocalDate.of(2024, 1, 2), 50.0, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    server.verify()
  }
}
