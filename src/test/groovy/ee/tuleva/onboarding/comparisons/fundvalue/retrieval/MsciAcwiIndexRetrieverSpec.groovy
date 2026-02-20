package ee.tuleva.onboarding.comparisons.fundvalue.retrieval


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.MsciAcwiIndexRetriever.PROVIDER
import static org.assertj.core.api.Assertions.assertThat
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(MsciAcwiIndexRetriever)
class MsciAcwiIndexRetrieverSpec extends Specification {

  @Autowired
  MsciAcwiIndexRetriever msciAcwiIndexRetriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
  }

  def "it is configured for the right index"() {
    when:
    def retrievalKey = msciAcwiIndexRetriever.getKey()
    then:
    retrievalKey == MsciAcwiIndexRetriever.KEY
  }

  def "it successfully fetches MSCI ACWI index values"() {
    given:
    def mockApiResponse = """
{
  "msci_index_code": "892400",
  "index_variant_type": "NETR",
  "ISO_currency_symbol": "EUR",
  "indexes": {
    "INDEX_LEVELS": [
      {
        "level_eod": 100.0,
        "calc_date": "20240102"
      },
      {
        "level_eod": 101.5,
        "calc_date": "20240103"
      },
      {
        "level_eod": 102.3,
        "calc_date": "20240104"
      }
    ]
  }
}
    """

    server.expect(requestTo("https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR&start_date=20240102&end_date=20240104&data_frequency=DAILY&baseValue=false&index_codes=892400"))
      .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))

    when:
    LocalDate startDate = LocalDate.of(2024, 1, 2)
    LocalDate endDate = LocalDate.of(2024, 1, 4)
    def result = msciAcwiIndexRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    def expected = [
      aFundValue(MsciAcwiIndexRetriever.KEY, LocalDate.of(2024, 1, 2), 100.0, PROVIDER),
      aFundValue(MsciAcwiIndexRetriever.KEY, LocalDate.of(2024, 1, 3), 101.5, PROVIDER),
      aFundValue(MsciAcwiIndexRetriever.KEY, LocalDate.of(2024, 1, 4), 102.3, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
  }

  def "it filters values within the requested date range"() {
    given:
    def mockApiResponse = """
{
  "msci_index_code": "892400",
  "index_variant_type": "NETR",
  "ISO_currency_symbol": "EUR",
  "indexes": {
    "INDEX_LEVELS": [
      {
        "level_eod": 99.0,
        "calc_date": "20240101"
      },
      {
        "level_eod": 100.0,
        "calc_date": "20240102"
      },
      {
        "level_eod": 101.5,
        "calc_date": "20240103"
      },
      {
        "level_eod": 102.3,
        "calc_date": "20240104"
      },
      {
        "level_eod": 103.0,
        "calc_date": "20240105"
      }
    ]
  }
}
    """

    server.expect(requestTo("https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph?output=INDEX_LEVELS&currency_symbol=EUR&index_variant=NETR&start_date=20240102&end_date=20240104&data_frequency=DAILY&baseValue=false&index_codes=892400"))
      .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))

    when:
    LocalDate startDate = LocalDate.of(2024, 1, 2)
    LocalDate endDate = LocalDate.of(2024, 1, 4)
    def result = msciAcwiIndexRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == 3
    def expected = [
      aFundValue(MsciAcwiIndexRetriever.KEY, LocalDate.of(2024, 1, 2), 100.0, PROVIDER),
      aFundValue(MsciAcwiIndexRetriever.KEY, LocalDate.of(2024, 1, 3), 101.5, PROVIDER),
      aFundValue(MsciAcwiIndexRetriever.KEY, LocalDate.of(2024, 1, 4), 102.3, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
  }
}
