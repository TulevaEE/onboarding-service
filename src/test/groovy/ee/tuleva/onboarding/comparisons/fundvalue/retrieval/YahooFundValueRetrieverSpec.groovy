package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.time.ClockConfig
import ee.tuleva.onboarding.time.ClockHolder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static java.math.BigDecimal.ZERO
import static java.time.ZoneOffset.UTC
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(YahooFundValueRetriever)
@Import(ClockConfig)
class YahooFundValueRetrieverSpec extends Specification {

  @Autowired
  YahooFundValueRetriever retriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
    ClockHolder.setDefaultClock()
  }

  def "it is configured for the right fund"() {
    when:
    def retrievalFund = retriever.getKey()
    then:
    retrievalFund == YahooFundValueRetriever.KEY
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

    YahooFundValueRetriever.FUND_TICKERS.forEach {
      fund -> server.expect(requestTo(String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&events=history&includeAdjustedClose=true&period1=1514764800&period2=1515110400", fund)))
        .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))
    }

    when:
    LocalDate startDate = LocalDate.of(2018, 1, 2)
    LocalDate endDate = LocalDate.of(2018, 1, 4)
    def result = retriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == YahooFundValueRetriever.FUND_TICKERS.size() * 3
    result.every { it.provider() == "YAHOO" }
    result.every { it.updatedAt() != null }
    YahooFundValueRetriever.FUND_TICKERS.each { ticker ->
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

    YahooFundValueRetriever.FUND_TICKERS.forEach {
      fund -> server.expect(requestTo(String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&events=history&includeAdjustedClose=true&period1=1514764800&period2=1515283200", fund)))
        .andRespond(withSuccess(mockApiResponseWithZeros, MediaType.APPLICATION_JSON))
    }

    when:
    LocalDate startDate = LocalDate.of(2018, 1, 2)
    LocalDate endDate = LocalDate.of(2018, 1, 6)
    def result = retriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == YahooFundValueRetriever.FUND_TICKERS.size() * 3
    result.every { fundValue -> fundValue.value() != ZERO }
    result.every { it.provider() == "YAHOO" }
    YahooFundValueRetriever.FUND_TICKERS.each { ticker ->
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

  def "it filters out null values from Yahoo Finance response"() {
    given:
    def mockApiResponseWithNulls = """
      {
        "chart": {
          "result": [
            {
              "timestamp": [1514876400, 1514962800, 1515049200, 1515135600, 1515222000],
              "indicators": {
                "adjclose": [
                  {
                    "adjclose": [13.5799999237061, null, 13.7229995727539, null, 13.85]
                  }
                ]
              }
            }
          ],
          "error": null
        }
      }
    """

    YahooFundValueRetriever.FUND_TICKERS.forEach {
      fund -> server.expect(requestTo(String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&events=history&includeAdjustedClose=true&period1=1514764800&period2=1515283200", fund)))
        .andRespond(withSuccess(mockApiResponseWithNulls, MediaType.APPLICATION_JSON))
    }

    when:
    LocalDate startDate = LocalDate.of(2018, 1, 2)
    LocalDate endDate = LocalDate.of(2018, 1, 6)
    def result = retriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == YahooFundValueRetriever.FUND_TICKERS.size() * 3
    result.every { fundValue -> fundValue.value() != null }
    result.every { it.provider() == "YAHOO" }
    YahooFundValueRetriever.FUND_TICKERS.each { ticker ->
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

  def "always excludes today's data"() {
    given:
    // 2018-01-04 20:00 UTC = 21:00 CET (well after any market close)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2018-01-04T20:00:00Z"), UTC))

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

    YahooFundValueRetriever.FUND_TICKERS.forEach {
      fund -> server.expect(requestTo(String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&events=history&includeAdjustedClose=true&period1=1514764800&period2=1515110400", fund)))
        .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))
    }

    when:
    LocalDate startDate = LocalDate.of(2018, 1, 2)
    LocalDate endDate = LocalDate.of(2018, 1, 4)
    def result = retriever.retrieveValuesForRange(startDate, endDate)

    then:
    // At 21:00 CET on Jan 4: latestFinalizedDate = Jan 3 (yesterday)
    // Jan 4 (today) is always excluded
    result.every { it.date() != LocalDate.of(2018, 1, 4) }
    result.any { it.date() == LocalDate.of(2018, 1, 2) }
    result.any { it.date() == LocalDate.of(2018, 1, 3) }
  }
}

