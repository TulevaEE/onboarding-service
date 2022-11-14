package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.Transaction
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RateOfReturnCalculatorSpec extends Specification {

  FundValueProvider fundValueProvider
  RateOfReturnCalculator rateOfReturnCalculator

  void setup() {
    fundValueProvider = Mock(FundValueProvider)
    rateOfReturnCalculator = new RateOfReturnCalculator(fundValueProvider)
  }

  def "it successfully calculates a return of 0%"() {
    given:
    Instant startTime = parseInstant("2018-06-17")
    Instant endTime = parseInstant("2018-06-18")
    fakeNoReturnFundValues()
    def overview = new AccountOverview([
        new Transaction(100.0, startTime),
        new Transaction(100.0, startTime),
    ], 0.0, 200.0, startTime, endTime, 2)
    when:
    def returnRateAndAmount = rateOfReturnCalculator.getReturn(overview)
    def estonianAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY)
    def marketAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    returnRateAndAmount.rate() == 0
    estonianAverageReturnRateAndAmount.rate() == 0
    marketAverageReturnRateAndAmount.rate() == 0
    returnRateAndAmount.amount() == 0
    estonianAverageReturnRateAndAmount.amount() == 0
    marketAverageReturnRateAndAmount.amount() == 0
    returnRateAndAmount.amount() == 0
    estonianAverageReturnRateAndAmount.amount() == 0
    marketAverageReturnRateAndAmount.amount() == 0
  }

  def "it successfully calculates a return for 0-valued transactions"() {
    given:
    Instant startTime = parseInstant("2018-06-17")
    Instant endTime = parseInstant("2018-06-18")
    fakeNoReturnFundValues()
    def overview = new AccountOverview([
        new Transaction(firstTransaction, startTime),
        new Transaction(secondTransaction, startTime),
    ], beginningBalance, endingBalance, startTime, endTime, 2)
    when:
    def returnRateAndAmount = rateOfReturnCalculator.getReturn(overview)
    def estonianAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY)
    def marketAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    returnRateAndAmount.rate() == BigDecimal.valueOf(xirr)
    estonianAverageReturnRateAndAmount.rate() == 0
    marketAverageReturnRateAndAmount.rate() == 0
    returnRateAndAmount.amount() == 0
    estonianAverageReturnRateAndAmount.amount() == 0
    marketAverageReturnRateAndAmount.amount() == 0

    where:
    firstTransaction | secondTransaction | beginningBalance | endingBalance || xirr
    0.0              | 0.0               | 0.0              | 0.0           || 0.0
    0.0              | 1.0               | 0.0              | 1.0           || 0.0
    0.0              | -1.0              | 1.0              | 0.0           || 0.0
  }

  def "it correctly calculates actual return taking into account the beginning balance"() {
    given:
    fakeNoReturnFundValues()

    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-18")
    def overview = new AccountOverview(exampleTransactions, 30.0, 620.0, startTime, endTime, 2)
    when:
    def returnRateAndAmount = rateOfReturnCalculator.getReturn(overview)
    def estonianAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY)
    def marketAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    returnRateAndAmount.rate() == 0.0427
    estonianAverageReturnRateAndAmount.rate() == 0
    marketAverageReturnRateAndAmount.rate() == 0
    returnRateAndAmount.amount() == 110
    estonianAverageReturnRateAndAmount.amount() == 0
    marketAverageReturnRateAndAmount.amount() == 0
  }

  def "it correctly calculates simulated return using a different fund taking into account the beginning balance"() {
    given:
    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-16")
    mockFundValues(EPIFundValueRetriever.KEY, epiFundValues())
    fundValueProvider.getLatestValue(UnionStockIndexRetriever.KEY, _) >> {
      String givenFund, LocalDate date -> Optional.of(new FundValue(givenFund, date, 123.0))
    }
    def overview = new AccountOverview(exampleTransactions, 30.0, 123123.0, startTime, endTime, 2)
    when:
    def estonianAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY)
    def marketAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    estonianAverageReturnRateAndAmount.rate() == 0.0326
    marketAverageReturnRateAndAmount.rate() == 0
    estonianAverageReturnRateAndAmount.amount().trunc(2) == 81.76
    marketAverageReturnRateAndAmount.amount().trunc(2) == 0
  }

  def "it handles missing fund values"() {
    given:
    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-16")
    fundValueProvider.getLatestValue(_, _) >> Optional.empty()
    def overview = new AccountOverview(exampleTransactions, 30.0, 123123.0, startTime, endTime, 2)
    when:
    def estonianAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, EPIFundValueRetriever.KEY)
    def marketAverageReturnRateAndAmount =
        rateOfReturnCalculator.getReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    estonianAverageReturnRateAndAmount.rate() == 0
    marketAverageReturnRateAndAmount.rate() == 0
    estonianAverageReturnRateAndAmount.amount() == 0
    marketAverageReturnRateAndAmount.amount() == 0
  }

  private static Map<String, BigDecimal> epiFundValues() {
    return [
        "2010-01-01": 133.00,
        "2010-07-01": 127.00,
        "2011-01-01": 140.00,
        "2011-07-01": 150.00,
        "2012-01-01": 145.00,
        "2012-07-01": 150.00,
        "2013-01-01": 152.50,
        "2013-07-01": 155.00,
        "2014-01-01": 157.50,
        "2014-07-01": 160.00,
        "2015-01-01": 162.50,
        "2015-07-01": 165.00,
        "2016-01-01": 167.50,
        "2016-07-01": 170.00,
        "2017-01-01": 172.50,
        "2017-07-01": 175.00,
        "2018-01-01": 177.50,
        "2018-07-16": 180.00,
    ]
  }

  private void mockFundValues(String fund, Map<String, BigDecimal> values) {
    fundValueProvider.getLatestValue(fund, _) >> {
      String givenFund, LocalDate date ->
        Optional.of(new FundValue(UnionStockIndexRetriever.KEY, null, values[date.toString()]))
    }
  }

  private static Instant parseInstant(String date) {
    return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant()
  }

  private void fakeNoReturnFundValues() {
    fundValueProvider.getLatestValue(_, _) >>
        Optional.of(new FundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2018-06-17"), 1.0))
  }

  List<Transaction> exampleTransactions = [
      new Transaction(30.0, parseInstant("2010-07-01")),
      new Transaction(30.0, parseInstant("2011-01-01")),
      new Transaction(30.0, parseInstant("2011-07-01")),
      new Transaction(30.0, parseInstant("2012-01-01")),
      new Transaction(30.0, parseInstant("2012-07-01")),
      new Transaction(30.0, parseInstant("2013-01-01")),
      new Transaction(30.0, parseInstant("2013-07-01")),
      new Transaction(30.0, parseInstant("2014-01-01")),
      new Transaction(30.0, parseInstant("2014-07-01")),
      new Transaction(30.0, parseInstant("2015-01-01")),
      new Transaction(30.0, parseInstant("2015-07-01")),
      new Transaction(30.0, parseInstant("2016-01-01")),
      new Transaction(30.0, parseInstant("2016-07-01")),
      new Transaction(30.0, parseInstant("2017-01-01")),
      new Transaction(30.0, parseInstant("2017-07-01")),
      new Transaction(30.0, parseInstant("2018-01-01")),
  ]
}
