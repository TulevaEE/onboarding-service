package ee.tuleva.onboarding.comparisons.returns


import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.AdjustedAcwiIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.Transaction
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.currency.Currency.EUR
import static java.time.temporal.ChronoUnit.DAYS

class ReturnCalculatorSpec extends Specification {

  FundValueProvider fundValueProvider
  FundPriceTiming fundPriceTiming
  ReturnCalculator returnCalculator

  void setup() {
    fundValueProvider = Mock(FundValueProvider)
    fundValueProvider.findValuesBetweenDates(_, _, _) >> { String key, LocalDate start, LocalDate end ->
      start.datesUntil(end.plusDays(1))
          .map { LocalDate date -> fundValueProvider.getLatestValue(key, date) }
          .filter { Optional<FundValue> value -> value.isPresent() && value.get().value() != null }
          .map { Optional<FundValue> value -> value.get() }
          .toList()
    }
    fundPriceTiming = Mock(FundPriceTiming)
    returnCalculator = new ReturnCalculator(fundValueProvider, fundPriceTiming)
  }

  def "it successfully calculates a return of 0%"() {
    given:
    Instant startTime = parseInstant("2018-06-17")
    Instant endTime = parseInstant("2018-06-18")
    fakeNoReturnFundValues()
    def overview = AccountOverview.builder()
        .transactions([
            new Transaction(100.0, startTime),
            new Transaction(100.0, startTime),
        ])
        .beginningBalance(0.0)
        .endingBalance(200.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()
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

  def "it returns no rate when the internal rate of return does not converge"() {
    given:
    Instant startTime = parseInstant("2018-06-17")
    Instant endTime = parseInstant("2018-06-18")
    def overview = AccountOverview.builder()
        .transactions([
            new Transaction(100.0, startTime),
        ])
        .beginningBalance(0.0)
        .endingBalance(1000000.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()

    when:
    def personalReturn = returnCalculator.getReturn(overview)

    then:
    personalReturn.rate() == null
    personalReturn.paymentsSum() == 100
  }

  def "it successfully calculates a return for 0-valued transactions"() {
    given:
    Instant startTime = parseInstant("2018-06-17")
    Instant endTime = parseInstant("2018-06-18")
    fakeNoReturnFundValues()
    def overview = AccountOverview.builder()
        .transactions([
            new Transaction(firstTransaction, startTime),
            new Transaction(secondTransaction, startTime),
        ])
        .beginningBalance(beginningBalance)
        .endingBalance(endingBalance)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()
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
    def overview = AccountOverview.builder()
        .transactions(exampleTransactions)
        .beginningBalance(30.0)
        .endingBalance(620.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()
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
    def overview = AccountOverview.builder()
        .transactions(exampleTransactions)
        .beginningBalance(30.0)
        .endingBalance(123123.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()
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

  def "prices every simulated purchase from one starting value and one load of the comparison fund's values"() {
    given:
    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-16")
    def epiValues = epiFundValues().collect { date, value ->
      aFundValue(EpiIndex.EPI.key, LocalDate.parse(date), value)
    }.sort { it.date() }
    def overview = AccountOverview.builder()
        .transactions(exampleTransactions)
        .beginningBalance(30.0)
        .endingBalance(123123.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()

    when:
    def estonianAverageReturn = returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)

    then:
    1 * fundValueProvider.getLatestValue(EpiIndex.EPI.key, _ as LocalDate) >> { String key, LocalDate date ->
      Optional.of(epiValues.findAll { !it.date().isAfter(date) }.last())
    }
    1 * fundValueProvider.findValuesBetweenDates(EpiIndex.EPI.key, _ as LocalDate, _ as LocalDate) >> epiValues
    0 * fundValueProvider.getLatestValue(_, _)
    estonianAverageReturn.rate() == 0.0326
    estonianAverageReturn.amount() == 81.76
    estonianAverageReturn.paymentsSum() == 16 * 30
  }

  def "has no rate for a comparison fund without values over the period"() {
    given:
    Instant startTime = parseInstant("2010-01-01")
    Instant endTime = parseInstant("2018-07-16")
    fundValueProvider.getLatestValue(_, _) >> Optional.empty()
    def overview = AccountOverview.builder()
        .transactions(exampleTransactions)
        .beginningBalance(30.0)
        .endingBalance(123123.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(2)
        .build()
    when:
    def estonianAverageReturn =
        returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)
    def marketAverageReturn =
        returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    estonianAverageReturn.rate() == null
    estonianAverageReturn.amount() == 0
    estonianAverageReturn.paymentsSum() == 0
    estonianAverageReturn.from() == LocalDate.parse("2010-01-01")
    estonianAverageReturn.to() == LocalDate.parse("2018-07-16")

    marketAverageReturn.rate() == null
    marketAverageReturn.amount() == 0
    marketAverageReturn.paymentsSum() == 0
    marketAverageReturn.from() == LocalDate.parse("2010-01-01")
    marketAverageReturn.to() == LocalDate.parse("2018-07-16")
  }

  def "buys the opening units at the first value of a fund whose values begin on the first day of the period"() {
    given:
    def fund = "EE3600001707"
    Instant startTime = parseInstant("2019-10-14")
    Instant endTime = parseInstant("2020-10-14")
    fundValueProvider.getLatestValue(fund, _ as LocalDate) >> { String key, LocalDate date ->
      date.isBefore(LocalDate.parse("2019-10-14")) ? Optional.empty() :
          date.isBefore(LocalDate.parse("2020-10-14")) ? Optional.of(aFundValue(fund, LocalDate.parse("2019-10-14"), 1.0)) :
              Optional.of(aFundValue(fund, LocalDate.parse("2020-10-14"), 1.1))
    }
    def overview = AccountOverview.builder()
        .transactions([])
        .beginningBalance(1000.0)
        .endingBalance(1050.0)
        .startTime(startTime)
        .endTime(endTime)
        .beginningBalanceTime(startTime.minus(1, DAYS))
        .endingBalanceTime(endTime)
        .pillar(3)
        .build()

    when:
    def fundReturn = returnCalculator.getSimulatedReturn(overview, fund)

    then:
    fundReturn == new ReturnDto(0.0994, 100.00, BigDecimal.ZERO, EUR, LocalDate.parse("2019-10-14"), LocalDate.parse("2020-10-14"))
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

  def "prices a contribution into a fund that follows the index a day late at the previous trading day's close"() {
    given:
    fundPriceTiming.sameDayWeight("DAY_LATE", AdjustedAcwiIndexRetriever.KEY, _) >> 0.0
    stubIndex(["2026-03-10": 100.0, "2026-03-11": 110.0, "2026-03-12": 120.0, "2026-03-13": 130.0])
    def overview = overviewEndingOn("2026-03-13",
        [new Transaction(1000.0, parseInstant("2026-03-11"), "DAY_LATE")], ["DAY_LATE": 1200.0])

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, AdjustedAcwiIndexRetriever.KEY)

    then:
    simulatedReturn.amount() == 200.00
  }

  def "skips the weekend rows that repeat Friday's close when finding the previous trading day's close"() {
    given:
    fundPriceTiming.sameDayWeight("DAY_LATE", UnionStockIndexRetriever.KEY, _) >> 0.0
    stubIndex(UnionStockIndexRetriever.KEY, [
        "2026-03-05": 100.0, "2026-03-06": 90.0, "2026-03-07": 90.0, "2026-03-08": 90.0,
        "2026-03-12": 120.0, "2026-03-13": 120.0])
    def overview = overviewEndingOn("2026-03-13",
        [new Transaction(1000.0, parseInstant("2026-03-08"), "DAY_LATE")], ["DAY_LATE": 1200.0])

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    simulatedReturn.amount() == (1000.0 / 100.0 * 120.0 - 1000.0).round(2)
  }

  def "prices the same day when a fund's timing cannot be estimated yet"() {
    given:
    fundPriceTiming.sameDayWeight("NEW_FUND", AdjustedAcwiIndexRetriever.KEY, _) >> null
    stubIndex(["2026-03-10": 100.0, "2026-03-11": 110.0, "2026-03-12": 120.0, "2026-03-13": 130.0])
    def overview = overviewEndingOn("2026-03-13",
        [new Transaction(1000.0, parseInstant("2026-03-11"), "NEW_FUND")], ["NEW_FUND": 1200.0])

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, AdjustedAcwiIndexRetriever.KEY)

    then:
    simulatedReturn.amount() == (1000.0 / 110.0 * 130.0 - 1000.0).round(2)
  }

  def "blends the day's close with the previous one by the fund's same-day weight"() {
    given:
    fundPriceTiming.sameDayWeight("MIXED", AdjustedAcwiIndexRetriever.KEY, _) >> 0.3
    stubIndex(["2026-03-10": 100.0, "2026-03-11": 110.0, "2026-03-12": 120.0, "2026-03-13": 130.0])
    def overview = overviewEndingOn("2026-03-13",
        [new Transaction(1000.0, parseInstant("2026-03-11"), "MIXED")], ["MIXED": 1200.0])

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, AdjustedAcwiIndexRetriever.KEY)

    then:
    def bought = Math.pow(110.0, 0.3) * Math.pow(100.0, 0.7)
    def sold = Math.pow(130.0, 0.3) * Math.pow(120.0, 0.7)
    simulatedReturn.amount() == (1000.0 / bought * sold - 1000.0).round(2)
  }

  def "values the opening balance with the timing of the funds held then, weighted by amount"() {
    given:
    fundPriceTiming.sameDayWeight("DAY_LATE", AdjustedAcwiIndexRetriever.KEY, _) >> 0.0
    fundPriceTiming.sameDayWeight("SAME_DAY", AdjustedAcwiIndexRetriever.KEY, _) >> 1.0
    stubIndex(["2026-02-26": 100.0, "2026-02-27": 110.0, "2026-03-12": 120.0, "2026-03-13": 120.0])
    def overview = AccountOverview.builder()
        .transactions([])
        .beginningBalance(1000.0)
        .endingBalance(1100.0)
        .startTime(parseInstant("2026-02-28"))
        .endTime(parseInstant("2026-03-13"))
        .beginningBalanceTime(parseInstant("2026-02-27"))
        .endingBalanceTime(parseInstant("2026-03-13"))
        .beginningHoldings(["DAY_LATE": 750.0, "SAME_DAY": 250.0])
        .endingHoldings(["SAME_DAY": 1100.0])
        .pillar(2)
        .build()

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, AdjustedAcwiIndexRetriever.KEY)

    then:
    def bought = Math.pow(110.0, 0.25) * Math.pow(100.0, 0.75)
    simulatedReturn.amount() == (1000.0 / bought * 120.0 - 1000.0).round(2)
  }

  def "prices comparisons other than the US-close world indexes at the day's own value"() {
    given:
    stubIndex(EpiIndex.EPI.key, ["2026-03-10": 100.0, "2026-03-11": 110.0, "2026-03-12": 120.0, "2026-03-13": 130.0])
    def overview = overviewEndingOn("2026-03-13",
        [new Transaction(1000.0, parseInstant("2026-03-11"), "DAY_LATE")], ["DAY_LATE": 1200.0])

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)

    then:
    simulatedReturn.amount() == (1000.0 / 110.0 * 130.0 - 1000.0).round(2)
    0 * fundPriceTiming.sameDayWeight(_, _, _)
  }

  def "real data: pricing at a day-late fund's timing removes most of a crash day's gap that the same day's close opens"() {
    given:
    fundPriceTiming.sameDayWeight("EE3600109435", AdjustedAcwiIndexRetriever.KEY, _) >> 0.27
    def contribution = { String date, BigDecimal amount -> new Transaction(amount, parseInstant(date), "EE3600109435") }
    def transactions = [
        contribution("2025-04-15", 706.66), contribution("2025-05-15", 706.66), contribution("2025-06-13", 706.66),
        contribution("2025-07-15", 706.66), contribution("2025-08-14", 706.66), contribution("2025-09-15", 706.66),
        contribution("2025-10-14", 706.66), contribution("2025-12-15", 706.66), contribution("2026-01-15", 706.66),
        contribution("2026-02-13", 707.01), contribution("2026-03-13", 707.01),
    ]
    def overview = AccountOverview.builder()
        .transactions(transactions)
        .beginningBalance(30809.92)
        .endingBalance(45244.78)
        .startTime(parseInstant("2025-04-07"))
        .endTime(parseInstant("2026-04-07"))
        .beginningBalanceTime(parseInstant("2025-04-06"))
        .endingBalanceTime(parseInstant("2026-04-07"))
        .beginningHoldings(["EE3600109435": 30809.92])
        .endingHoldings(["EE3600109435": 45244.78])
        .pillar(2)
        .build()

    and:
    stubIndex([
        "2025-04-02": 371.94814, "2025-04-03": 350.85246, "2025-04-04": 335.96977, "2025-04-07": 329.70106,
        "2025-04-14": 340.33268, "2025-04-15": 342.63989, "2025-05-14": 377.38254, "2025-05-15": 379.38728,
        "2025-06-12": 376.71247, "2025-06-13": 373.85824, "2025-07-14": 383.77719, "2025-07-15": 384.61398,
        "2025-08-13": 395.16376, "2025-08-14": 396.72494, "2025-09-12": 403.26630, "2025-09-15": 404.09045,
        "2025-10-13": 413.27711, "2025-10-14": 410.92449, "2025-12-12": 419.30358, "2025-12-15": 418.01996,
        "2026-01-14": 434.00590, "2026-01-15": 436.94631, "2026-02-12": 429.53483, "2026-02-13": 429.85173,
        "2026-03-12": 427.76519, "2026-03-13": 427.07497, "2026-04-06": 423.33994, "2026-04-07": 422.42101,
    ])

    when:
    def personalReturn = returnCalculator.getReturn(overview)
    def atTheFundsTiming = returnCalculator.getSimulatedReturn(overview, AdjustedAcwiIndexRetriever.KEY)
    def atTheSameDaysClose = returnCalculator.getSimulatedReturn(AccountOverview.builder()
        .transactions(transactions.collect { new Transaction(it.amount(), it.time()) })
        .beginningBalance(30809.92)
        .endingBalance(45244.78)
        .startTime(parseInstant("2025-04-07"))
        .endTime(parseInstant("2026-04-07"))
        .beginningBalanceTime(parseInstant("2025-04-06"))
        .endingBalanceTime(parseInstant("2026-04-07"))
        .pillar(2)
        .build(), AdjustedAcwiIndexRetriever.KEY)

    then:
    personalReturn.rate() == 0.1907
    atTheFundsTiming.rate() == 0.2081
    atTheSameDaysClose.rate() == 0.2405
  }

  def "annualizes the personal return over the days between the prices that valued the balances"() {
    given:
    def overview = AccountOverview.builder()
        .transactions([])
        .beginningBalance(10000.0)
        .endingBalance(12110.0)
        .startTime(parseInstant("2025-10-01"))
        .endTime(parseInstant("2026-10-01"))
        .beginningBalanceTime(parseInstant("2025-09-30"))
        .endingBalanceTime(parseInstant("2026-09-30"))
        .pillar(2)
        .build()

    when:
    def personalReturn = returnCalculator.getReturn(overview)

    then:
    personalReturn.rate() == 0.2110
    personalReturn.from() == LocalDate.parse("2025-10-01")
    personalReturn.to() == LocalDate.parse("2026-10-01")
  }

  def "buys the comparison's opening units at the price before the start whenever the balance was priced"() {
    given:
    def overview = AccountOverview.builder()
        .transactions([])
        .beginningBalance(10000.0)
        .endingBalance(12000.0)
        .startTime(parseInstant("2025-04-09"))
        .endTime(parseInstant("2026-04-09"))
        .beginningBalanceTime(parseInstant("2025-04-07"))
        .endingBalanceTime(parseInstant("2026-04-09"))
        .pillar(2)
        .build()
    fundValueProvider.getLatestValue(UnionStockIndexRetriever.KEY, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-04-07": 370.0,
          "2025-04-08": 390.0,
          "2026-04-09": 480.0,
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, UnionStockIndexRetriever.KEY)

    then:
    simulatedReturn.amount() == 2307.69
  }

  def "values the comparison on the day the closing balance was priced"() {
    given:
    def overview = AccountOverview.builder()
        .transactions([])
        .beginningBalance(10000.0)
        .endingBalance(12000.0)
        .startTime(parseInstant("2025-10-01"))
        .endTime(parseInstant("2026-10-01"))
        .beginningBalanceTime(parseInstant("2025-09-30"))
        .endingBalanceTime(parseInstant("2026-09-29"))
        .pillar(3)
        .build()
    fundValueProvider.getLatestValue(EpiIndex.EPI.key, _ as LocalDate) >> { String fund, LocalDate date ->
      def values = [
          "2025-09-30": 400.0,
          "2026-09-29": 480.0,
          "2026-09-30": 500.0,
          "2026-10-01": 500.0,
      ]
      def value = values[date.toString()]
      value != null ? Optional.of(aFundValue(fund, date, value)) : Optional.empty()
    }

    when:
    def simulatedReturn = returnCalculator.getSimulatedReturn(overview, EpiIndex.EPI.key)

    then:
    simulatedReturn.amount() == 2000.00
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

  private void stubIndex(Map<String, BigDecimal> closes) {
    stubIndex(AdjustedAcwiIndexRetriever.KEY, closes)
  }

  private void stubIndex(String key, Map<String, BigDecimal> closes) {
    fundValueProvider.getLatestValue(key, _ as LocalDate) >> { String fund, LocalDate date ->
      def latest = closes.keySet().findAll { !LocalDate.parse(it).isAfter(date) }.max()
      latest == null ? Optional.empty() : Optional.of(aFundValue(fund, LocalDate.parse(latest), closes[latest]))
    }
  }

  private static AccountOverview overviewEndingOn(String endDate, List<Transaction> transactions, Map<String, BigDecimal> endingHoldings) {
    AccountOverview.builder()
        .transactions(transactions)
        .beginningBalance(0.0)
        .endingBalance(endingHoldings.values().sum() as BigDecimal)
        .startTime(parseInstant("2026-03-01"))
        .endTime(parseInstant(endDate))
        .beginningBalanceTime(parseInstant("2026-02-28"))
        .endingBalanceTime(parseInstant(endDate))
        .endingHoldings(endingHoldings)
        .pillar(2)
        .build()
  }
}
