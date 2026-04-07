package ee.tuleva.onboarding.comparisons.returns


import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.deadline.PublicHolidays
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue

class ReturnCalculatorSpec extends Specification {

  FundValueProvider fundValueProvider
  ReturnCalculator returnCalculator

  void setup() {
    fundValueProvider = Mock(FundValueProvider)
    returnCalculator = new ReturnCalculator(fundValueProvider, new PublicHolidays())
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
    def personalReturn = returnCalculator.getReturn(overview)
    def estonianAverageReturn =
        returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)
    def marketAverageReturn =
        returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    personalReturn.rate() == 0
    personalReturn.amount() == 0
    personalReturn.paymentsSum() == 100 + 100
    personalReturn.from() == LocalDate.parse("2018-06-17")
    personalReturn.to() == LocalDate.parse("2018-06-18")

    estonianAverageReturn.rate() == 0
    estonianAverageReturn.amount() == 0
    estonianAverageReturn.paymentsSum() == 100 + 100
    estonianAverageReturn.from() == LocalDate.parse("2018-06-17")
    estonianAverageReturn.to() == LocalDate.parse("2018-06-18")

    marketAverageReturn.rate() == 0
    marketAverageReturn.amount() == 0
    marketAverageReturn.paymentsSum() == 100 + 100
    marketAverageReturn.from() == LocalDate.parse("2018-06-17")
    marketAverageReturn.to() == LocalDate.parse("2018-06-18")
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
    def personalReturn = returnCalculator.getReturn(overview)
    def estonianAverageReturn =
        returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)
    def marketAverageReturn =
        returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    personalReturn.rate() == BigDecimal.valueOf(xirr)
    personalReturn.amount() == 0
    personalReturn.paymentsSum() == firstTransaction + secondTransaction
    personalReturn.from() == LocalDate.parse("2018-06-17")
    personalReturn.to() == LocalDate.parse("2018-06-18")

    estonianAverageReturn.rate() == 0
    estonianAverageReturn.amount() == 0
    personalReturn.paymentsSum() == firstTransaction + secondTransaction
    estonianAverageReturn.from() == LocalDate.parse("2018-06-17")
    estonianAverageReturn.to() == LocalDate.parse("2018-06-18")

    marketAverageReturn.rate() == 0
    marketAverageReturn.amount() == 0
    marketAverageReturn.paymentsSum() == firstTransaction + secondTransaction
    marketAverageReturn.from() == LocalDate.parse("2018-06-17")
    marketAverageReturn.to() == LocalDate.parse("2018-06-18")

    where:
    firstTransaction | secondTransaction | beginningBalance | endingBalance || xirr
    0.0              | 0.0               | 0.0              | 0.0           || 0.0
    0.0              | 0.0               | 0.000000         | 0.0           || 0.0
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
    def personalReturn = returnCalculator.getReturn(overview)
    def estonianAverageReturn =
        returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)
    def marketAverageReturn =
        returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    personalReturn.rate() == 0.0427
    personalReturn.amount() == 110
    personalReturn.paymentsSum() == 16 * 30
    personalReturn.from() == LocalDate.parse("2010-01-01")
    personalReturn.to() == LocalDate.parse("2018-07-18")

    estonianAverageReturn.rate() == 0
    estonianAverageReturn.amount() == 0
    personalReturn.paymentsSum() == 16 * 30
    estonianAverageReturn.from() == LocalDate.parse("2010-01-01")
    estonianAverageReturn.to() == LocalDate.parse("2018-07-18")

    marketAverageReturn.rate() == 0
    marketAverageReturn.amount() == 0
    personalReturn.paymentsSum() == 16 * 30
    marketAverageReturn.from() == LocalDate.parse("2010-01-01")
    marketAverageReturn.to() == LocalDate.parse("2018-07-18")
  }

  def "it correctly calculates simulated return using a different fund taking into account the beginning balance"() {
    given:
    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-16")
    mockFundValues(EpiIndex.EPI.key, epiFundValues())
    fundValueProvider.getLatestValue(UnionStockIndexRetriever.KEY, _ as LocalDate) >> {
      String givenFund, LocalDate date -> Optional.of(aFundValue(givenFund, date, 123.0))
    }
    def overview = new AccountOverview(exampleTransactions, 30.0, 123123.0, startTime, endTime, 2)
    when:
    def estonianAverageReturn =
        returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)
    def marketAverageReturn =
        returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    estonianAverageReturn.rate() == 0.0326
    estonianAverageReturn.amount() == 81.76
    estonianAverageReturn.paymentsSum() == 16 * 30
    estonianAverageReturn.from() == LocalDate.parse("2010-01-01")
    estonianAverageReturn.to() == LocalDate.parse("2018-07-16")

    marketAverageReturn.rate() == 0
    marketAverageReturn.amount() == 0
    marketAverageReturn.paymentsSum() == 16 * 30
    marketAverageReturn.from() == LocalDate.parse("2010-01-01")
    marketAverageReturn.to() == LocalDate.parse("2018-07-16")
  }

  def "it handles missing fund values"() {
    given:
    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-16")
    fundValueProvider.getLatestValue(_, _) >> Optional.empty()
    def overview = new AccountOverview(exampleTransactions, 30.0, 123123.0, startTime, endTime, 2)
    when:
    def estonianAverageReturn =
        returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)
    def marketAverageReturn =
        returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    estonianAverageReturn.rate() == 0
    estonianAverageReturn.amount() == 0
    estonianAverageReturn.paymentsSum() == 0
    estonianAverageReturn.from() == LocalDate.parse("2010-01-01")
    estonianAverageReturn.to() == LocalDate.parse("2018-07-16")

    marketAverageReturn.rate() == 0
    marketAverageReturn.amount() == 0
    marketAverageReturn.paymentsSum() == 0
    marketAverageReturn.from() == LocalDate.parse("2010-01-01")
    marketAverageReturn.to() == LocalDate.parse("2018-07-16")
  }

  private static Map<String, BigDecimal> epiFundValues() {
    return [
        "2009-12-31": 133.00,
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
    fundValueProvider.getLatestValue(fund, _ as LocalDate) >> {
      String givenFund, LocalDate date ->
        Optional.of(aFundValue(UnionStockIndexRetriever.KEY, date, values[date.toString()]))
    }
  }

  private static Instant parseInstant(String date) {
    return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant()
  }

  private void fakeNoReturnFundValues() {
    fundValueProvider.getLatestValue(_, _) >>
        Optional.of(aFundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2018-06-17"), 1.0))
  }

  def "aligns beginning balance index lookup when synthetic date crosses weekend"() {
    given: "start dates where the synthetic transaction (startTime - 1 day) lands on a weekend"
    Instant startTime = parseInstant(startDate)
    Instant endTime = parseInstant(endDate)
    def overview = new AccountOverview([], 10000.0, 12000.0, startTime, endTime, 2)

    and: "index prices that reflect a crash: Thu=400, Fri=380 (5% drop), end=480"
    def indexKey = UnionStockIndexRetriever.KEY
    fundValueProvider.getLatestValue(indexKey, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-04-03": 400.0,  // Thursday (pre-Friday crash)
          "2025-04-04": 380.0,  // Friday (post-crash US close)
          "2025-04-05": 380.0,  // Saturday (carry-forward)
          "2025-04-06": 380.0,  // Sunday (carry-forward)
          "2026-04-06": 480.0,
          "2026-04-07": 480.0,
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, indexKey)

    then: "uses Thursday price (400) not Friday (380), so ending = 10000/400 * 480 = 12000"
    simulatedReturn.amount() == 2000.00

    where: "both Sunday and Monday starts have synthetic dates on weekend"
    startDate       | endDate
    "2025-04-06"    | "2026-04-06"   // Sunday start → synthetic Sat
    "2025-04-07"    | "2026-04-07"   // Monday start → synthetic Sun
  }

  def "weekday start with weekday synthetic date uses standard index lookup"() {
    given: "a Wednesday start where synthetic date (Tuesday) is a working day"
    Instant startTime = parseInstant("2025-04-09") // Wednesday
    Instant endTime = parseInstant("2026-04-09")
    def overview = new AccountOverview([], 10000.0, 12000.0, startTime, endTime, 2)

    and: "index prices where Tuesday and Monday differ"
    def indexKey = UnionStockIndexRetriever.KEY
    fundValueProvider.getLatestValue(indexKey, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-04-07": 370.0,  // Monday (would be wrong if fix over-applied)
          "2025-04-08": 390.0,  // Tuesday (synthetic date = Wed - 1 day)
          "2026-04-09": 480.0,  // End date
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, indexKey)

    then: "uses Tuesday price (390), so ending = 10000/390 * 480 = 12307.69"
    simulatedReturn.amount() == 2307.69
  }

  def "weekend start does not adjust index lookup for non-UNION_STOCK_INDEX comparisons"() {
    given: "a Sunday start with a beginning balance, comparing against EPI (European-close pricing)"
    Instant startTime = parseInstant("2025-04-06") // Sunday
    Instant endTime = parseInstant("2026-04-06")
    def overview = new AccountOverview([], 10000.0, 12000.0, startTime, endTime, 2)

    and: "EPI prices where Friday (standard lookup) differs from Thursday (shifted lookup)"
    def epiKey = EpiIndex.EPI.key
    fundValueProvider.getLatestValue(epiKey, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-04-03": 999.0,  // Thursday — would be used if fix incorrectly applied
          "2025-04-04": 400.0,  // Friday — should be used (standard lookup via Sat carry-back)
          "2025-04-05": 400.0,  // Saturday (synthetic date)
          "2026-04-06": 480.0,  // End date
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, epiKey)

    then: "uses Friday price (400) not Thursday (999), so ending = 10000/400 * 480 = 12000"
    simulatedReturn.amount() == 2000.00
  }

  def "weekend start with zero beginning balance does not adjust index lookup"() {
    given: "a Sunday start with no beginning balance"
    Instant startTime = parseInstant("2025-04-06") // Sunday
    Instant endTime = parseInstant("2026-04-06")
    def overview = new AccountOverview(
        [new Transaction(1000.0, parseInstant("2025-05-01"))],
        0.0, 1200.0, startTime, endTime, 2)

    and: "index prices for the contribution and end date"
    def indexKey = UnionStockIndexRetriever.KEY
    fundValueProvider.getLatestValue(indexKey, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-05-01": 400.0,  // Contribution date
          "2026-04-06": 480.0,  // End date
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, indexKey)

    then: "contribution uses standard lookup: 1000/400 * 480 = 1200"
    simulatedReturn.amount() == 200.00 // 1200 - 1000
  }

  def "real data: Monday start during April 2025 crash reduces index gap from 5pp to 1pp"() {
    given: "real 2nd pillar cash flows for a Monday start during the tariff crash"
    Instant startTime = parseInstant("2025-04-07") // Monday — synthetic date is Sunday
    Instant endTime = parseInstant("2026-04-07")
    def beginningBalance = 30809.92
    def endingBalance = 45244.78

    def transactions = [
        new Transaction(706.66, parseInstant("2025-04-15")),
        new Transaction(706.66, parseInstant("2025-05-15")),
        new Transaction(706.66, parseInstant("2025-06-13")),
        new Transaction(706.66, parseInstant("2025-07-15")),
        new Transaction(706.66, parseInstant("2025-08-14")),
        new Transaction(706.66, parseInstant("2025-09-15")),
        new Transaction(706.66, parseInstant("2025-10-14")),
        new Transaction(706.66, parseInstant("2025-12-15")),
        new Transaction(706.66, parseInstant("2026-01-15")),
        new Transaction(707.01, parseInstant("2026-02-13")),
        new Transaction(707.01, parseInstant("2026-03-13")),
    ]
    def overview = new AccountOverview(
        transactions, beginningBalance, endingBalance, startTime, endTime, 2)

    and: "real UNION_STOCK_INDEX values from prod DB"
    def indexKey = UnionStockIndexRetriever.KEY
    fundValueProvider.getLatestValue(indexKey, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-04-03": 402.09336,  // Thursday (fix uses this for beginning balance)
          "2025-04-04": 385.62221,  // Friday US close
          "2025-04-05": 385.62221,  // Saturday (carry-forward)
          "2025-04-06": 385.62221,  // Sunday (carry-forward, synthetic date lands here)
          "2025-04-15": 392.58059,
          "2025-05-15": 434.49828,
          "2025-06-13": 428.33231,
          "2025-07-15": 440.81159,
          "2025-08-14": 454.53635,
          "2025-09-15": 463.28622,
          "2025-10-14": 470.54212,
          "2025-12-15": 478.99370,
          "2026-01-15": 501.72402,
          "2026-02-13": 494.78222,
          "2026-03-13": 490.46782,
          "2026-04-06": 486.55963,
          "2026-04-07": 486.55963,  // end date (same as Apr 6, latest available)
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def personalReturn = returnCalculator.getReturn(overview)
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, indexKey)

    then: "personal return matches the real API value"
    personalReturn.rate() == 0.1907

    and: "index return is close to personal (within ~1pp tracking difference), not 5pp inflated"
    simulatedReturn.rate() == 0.1998
    (simulatedReturn.rate() - personalReturn.rate()) < 0.02 // less than 2pp gap
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
