package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever.EUROPE_BERLIN
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever.MARKET_CLOSE_BUFFER
import static java.math.BigDecimal.ZERO
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(YahooFundValueRetriever)
class YahooFundValueRetrieverSpec extends Specification {

  @Autowired
  YahooFundValueRetriever retriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
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

  def "filters out today's values when market is still open"() {
    given:
    def beforeClose = MARKET_CLOSE_BUFFER.minusHours(1)
    def fixedTime = ZonedDateTime.of(2024, 1, 15, beforeClose.getHour(), beforeClose.getMinute(), 0, 0, EUROPE_BERLIN)
    def clock = Clock.fixed(fixedTime.toInstant(), EUROPE_BERLIN)
    def testRetriever = new YahooFundValueRetriever(RestClient.builder(), clock)

    def now = Instant.now()
    def today = LocalDate.of(2024, 1, 15)
    def yesterday = LocalDate.of(2024, 1, 14)
    def values = [
        new FundValue("FUND1", yesterday, BigDecimal.TEN, "YAHOO", now),
        new FundValue("FUND1", today, BigDecimal.ONE, "YAHOO", now)
    ]

    when:
    def filtered = testRetriever.filterIntradayValues(values)

    then:
    filtered == [new FundValue("FUND1", yesterday, BigDecimal.TEN, "YAHOO", now)]
  }

  def "keeps today's values when market has closed"() {
    given:
    def afterClose = MARKET_CLOSE_BUFFER.plusHours(1)
    def fixedTime = ZonedDateTime.of(2024, 1, 15, afterClose.getHour(), afterClose.getMinute(), 0, 0, EUROPE_BERLIN)
    def clock = Clock.fixed(fixedTime.toInstant(), EUROPE_BERLIN)
    def testRetriever = new YahooFundValueRetriever(RestClient.builder(), clock)

    def now = Instant.now()
    def today = LocalDate.of(2024, 1, 15)
    def yesterday = LocalDate.of(2024, 1, 14)
    def values = [
        new FundValue("FUND1", yesterday, BigDecimal.TEN, "YAHOO", now),
        new FundValue("FUND1", today, BigDecimal.ONE, "YAHOO", now)
    ]

    when:
    def filtered = testRetriever.filterIntradayValues(values)

    then:
    filtered == values
  }

  def "isAfterMarketClose returns true when current time is after market close buffer"() {
    given:
    def afterClose = MARKET_CLOSE_BUFFER.plusMinutes(1)
    def fixedTime = ZonedDateTime.of(2024, 1, 15, afterClose.getHour(), afterClose.getMinute(), 0, 0, EUROPE_BERLIN)
    def clock = Clock.fixed(fixedTime.toInstant(), EUROPE_BERLIN)
    def testRetriever = new YahooFundValueRetriever(RestClient.builder(), clock)

    expect:
    testRetriever.isAfterMarketClose()
  }

  def "isAfterMarketClose returns false when current time is before market close buffer"() {
    given:
    def beforeClose = MARKET_CLOSE_BUFFER.minusMinutes(1)
    def fixedTime = ZonedDateTime.of(2024, 1, 15, beforeClose.getHour(), beforeClose.getMinute(), 0, 0, EUROPE_BERLIN)
    def clock = Clock.fixed(fixedTime.toInstant(), EUROPE_BERLIN)
    def testRetriever = new YahooFundValueRetriever(RestClient.builder(), clock)

    expect:
    !testRetriever.isAfterMarketClose()
  }

  def "isAfterMarketClose returns false when current time is exactly at market close buffer"() {
    given:
    def fixedTime = ZonedDateTime.of(2024, 1, 15, MARKET_CLOSE_BUFFER.getHour(), MARKET_CLOSE_BUFFER.getMinute(), 0, 0, EUROPE_BERLIN)
    def clock = Clock.fixed(fixedTime.toInstant(), EUROPE_BERLIN)
    def testRetriever = new YahooFundValueRetriever(RestClient.builder(), clock)

    expect:
    !testRetriever.isAfterMarketClose()
  }
}

