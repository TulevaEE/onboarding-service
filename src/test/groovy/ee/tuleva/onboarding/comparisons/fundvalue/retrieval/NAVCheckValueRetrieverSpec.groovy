package ee.tuleva.onboarding.comparisons.fundvalue.retrieval


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.LocalDate

import static java.math.BigDecimal.ZERO
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(NAVCheckValueRetriever)
class NAVCheckValueRetrieverSpec extends Specification {

  @Autowired
  NAVCheckValueRetriever navCheckValueRetriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
  }

  def "it is configured for the right fund"() {
    when:
    def retrievalFund = navCheckValueRetriever.getKey()
    then:
    retrievalFund == NAVCheckValueRetriever.KEY
  }

  def "it successfully fetches quotes for all funds"() {
    given:
    def mockApiResponse = """
      {
        "chart": {
          "result": [
            {
              "timestamp": [1514876400, 1514962800, 1515049200],
              "indicators": {
      
                "adjclose": [
                  {
                    "adjclose": [13.5799999237061, 13.6680002212524, 13.7229995727539]
                  }
                ]
              }
            }
          ],
          "error": null
        }
      }
    """

    NAVCheckValueRetriever.FUND_TICKERS.forEach {
      fund -> server.expect(requestTo(String.format("https://query1.finance.yahoo.com/v7/finance/chart/%s?interval=1d&events=history&includeAdjustedClose=true&period1=1514764800&period2=1515110400", fund)))
        .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))
    }

    when:
    LocalDate startDate = LocalDate.of(2018, 1, 2)
    LocalDate endDate = LocalDate.of(2018, 1, 4)
    def result = navCheckValueRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == NAVCheckValueRetriever.FUND_TICKERS.size() * 3
    result.every { it.provider() == "YAHOO" }
    result.every { it.updatedAt() != null }
    NAVCheckValueRetriever.FUND_TICKERS.each { ticker ->
      def tickerValues = result.findAll { it.key() == ticker }
      assert tickerValues.size() == 3
      assert tickerValues[0].date() == LocalDate.of(2018, 1, 2)
      assert tickerValues[0].value() == 13.5799999237061
      assert tickerValues[1].date() == LocalDate.of(2018, 1, 3)
      assert tickerValues[1].value() == 13.6680002212524
      assert tickerValues[2].date() == LocalDate.of(2018, 1, 4)
      assert tickerValues[2].value() == 13.7229995727539
    }
  }

  def "it filters out zero values from Yahoo Finance response"() {
    given:
    def mockApiResponseWithZeros = """
      {
        "chart": {
          "result": [
            {
              "timestamp": [1514876400, 1514962800, 1515049200, 1515135600, 1515222000],
              "indicators": {
                "adjclose": [
                  {
                    "adjclose": [13.5799999237061, 0.0, 13.7229995727539, 0.0, 13.85]
                  }
                ]
              }
            }
          ],
          "error": null
        }
      }
    """

    NAVCheckValueRetriever.FUND_TICKERS.forEach {
      fund -> server.expect(requestTo(String.format("https://query1.finance.yahoo.com/v7/finance/chart/%s?interval=1d&events=history&includeAdjustedClose=true&period1=1514764800&period2=1515283200", fund)))
        .andRespond(withSuccess(mockApiResponseWithZeros, MediaType.APPLICATION_JSON))
    }

    when:
    LocalDate startDate = LocalDate.of(2018, 1, 2)
    LocalDate endDate = LocalDate.of(2018, 1, 6)
    def result = navCheckValueRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == NAVCheckValueRetriever.FUND_TICKERS.size() * 3
    result.every { fundValue -> fundValue.value() != ZERO }
    result.every { it.provider() == "YAHOO" }
    NAVCheckValueRetriever.FUND_TICKERS.each { ticker ->
      def tickerValues = result.findAll { it.key() == ticker }
      assert tickerValues.size() == 3
      assert tickerValues[0].date() == LocalDate.of(2018, 1, 2)
      assert tickerValues[0].value() == 13.5799999237061
      assert tickerValues[1].date() == LocalDate.of(2018, 1, 4)
      assert tickerValues[1].value() == 13.7229995727539
      assert tickerValues[2].date() == LocalDate.of(2018, 1, 6)
      assert tickerValues[2].value() == 13.85
    }
  }
}

