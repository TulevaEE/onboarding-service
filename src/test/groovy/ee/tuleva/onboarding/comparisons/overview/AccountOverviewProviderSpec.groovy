package ee.tuleva.onboarding.comparisons.overview

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.epis.CashFlow
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.CashFlowStatement
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.time.TestClockHolder
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.CashFlowFixture.cashFlowFixture
import static java.time.temporal.ChronoUnit.DAYS

class AccountOverviewProviderSpec extends Specification {

    FundRepository fundRepository
    EpisService episService
    FundValueProvider fundValueProvider
    AccountOverviewProvider accountOverviewProvider

    Clock clock = TestClockHolder.clock
    Person person = samplePerson()
    LocalDate startDate = LocalDate.parse("1998-01-01")
    Instant startTime = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    Instant endTime = clock.instant()
    LocalDate endDate = LocalDateTime.ofInstant(endTime, ZoneOffset.UTC).toLocalDate()
    CashFlowStatement cashFlowStatement = cashFlowFixture()
    def pillar = 2

    def setup() {
        fundRepository = Mock(FundRepository)
        episService = Mock(EpisService)
        fundValueProvider = Mock(FundValueProvider, defaultResponse: EmptyOrDummyResponse.INSTANCE)
        accountOverviewProvider = new AccountOverviewProvider(fundRepository, episService, new BalancePriceDates(fundValueProvider), clock)
        fundRepository.findAllByPillar(pillar) >> [
            Fund.builder().isin("1").build(),
            Fund.builder().isin("2").build(),
        ]
        episService.getCashFlowStatement(person, startDate, endDate) >> cashFlowStatement
    }

    def "it sets the right start and end times"() {
        when:
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, startTime, endTime, pillar)
        then:
        accountOverview.startTime == startTime
        verifyTimeCloseToNow(accountOverview.endTime)
    }

    def "it bunches together and converts the starting and ending balance"() {
        when:
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, startTime, endTime, pillar)
        then:
        accountOverview.beginningBalance == 1000.00 + 115.00
        accountOverview.endingBalance == 1100.00 + 125.00
    }

    def "it converts all transactions"() {
        when:
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, startTime, endTime, pillar)
        then:
        accountOverview.pillar == pillar
        accountOverview.transactions.size() == 2
        accountOverview.transactions[0].amount == cashFlowStatement.transactions[0].amount
        accountOverview.transactions[0].time == cashFlowStatement.transactions[0].priceTime
        accountOverview.transactions[1].amount == cashFlowStatement.transactions[1].amount
        accountOverview.transactions[1].time == cashFlowStatement.transactions[1].priceTime
    }

  def "when a start time is set in the future, then set the end time to the given start time to avoid invalid arguments"() {
    given:
        CashFlowStatement cashFlowStatement = cashFlowFixture()
        Instant futureStartTime = clock.instant().plus(2, DAYS)
        def futureStartDate = LocalDateTime.ofInstant(futureStartTime, ZoneOffset.UTC).toLocalDate()

    when:
        1 * episService.getCashFlowStatement(person, futureStartDate, futureStartDate) >> cashFlowStatement
        AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, futureStartTime, endTime, pillar)

    then:
        0 * episService.getCashFlowStatement(person, futureStartDate, endDate)
        accountOverview.transactions.size() == 2
  }

  def "dates the beginning balance at the last price before the start that valued it"() {
    given:
    LocalDate monday = LocalDate.parse("2019-10-07")
    statementFrom(monday, [balance("1", 1000.0, 1.10)], [balance("1", 1210.0, 1.21)])
    pricesOf("1", ["2019-10-03": 1.09, "2019-10-04": 1.10, "2019-10-07": 1.12])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(monday), endTime, pillar)

    then:
    accountOverview.beginningBalanceTime == atStartOfDay(LocalDate.parse("2019-10-04"))
  }

  def "dates the ending balance at the latest price on or before the end that valued it"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start, [balance("1", 1000.0, 1.10)], [balance("1", 1210.0, 1.21)])
    pricesOf("1", ["2019-09-30": 1.10, (endDate.minusDays(2).toString()): 1.20, (endDate.minusDays(1).toString()): 1.21])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.endingBalanceTime == atStartOfDay(endDate.minusDays(1))
  }

  def "dates the ending balance at the price before the latest stored one when the statement was valued before it was stored"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start, [balance("1", 1000.0, 1.10)], [balance("1", 1210.0, 1.21)])
    pricesOf("1", ["2019-09-30": 1.10, (endDate.minusDays(1).toString()): 1.21, (endDate.toString()): 1.25])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.endingBalanceTime == atStartOfDay(endDate.minusDays(1))
  }

  def "keeps the end when the statement is valued at a price not stored yet"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start, [balance("1", 1000.0, 1.10)], [balance("1", 1250.0, 1.25)])
    pricesOf("1", ["2019-09-30": 1.10, (endDate.minusDays(2).toString()): 1.20, (endDate.minusDays(1).toString()): 1.21])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.endingBalanceTime == endTime
  }

  def "keeps the day before the start when the stored prices of the fund have stopped"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start, [balance("1", 1000.0, 1.10)], [balance("1", 1210.0, 1.21)])
    pricesOf("1", ["2019-02-04": 0.95, "2019-02-05": 0.96])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.beginningBalanceTime == atStartOfDay(start).minus(1, DAYS)
    accountOverview.endingBalanceTime == endTime
  }

  def "keeps the day before the start and the end when the statement carries no unit price"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start, [balance("1", 1000.0, null)], [balance("1", 1210.0, null)])
    pricesOf("1", ["2019-09-30": 1.10, (endDate.minusDays(1).toString()): 1.21])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.beginningBalanceTime == atStartOfDay(start).minus(1, DAYS)
    accountOverview.endingBalanceTime == endTime
  }

  def "dates a balance spread over funds by the latest of their prices"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start,
        [balance("1", 1000.0, 1.10), balance("2", 500.0, 2.00)],
        [balance("1", 1210.0, 1.21), balance("2", 600.0, 2.40)])
    pricesOf("1", ["2019-09-30": 1.10, (endDate.minusDays(2).toString()): 1.21])
    pricesOf("2", ["2019-09-30": 2.00, (endDate.minusDays(1).toString()): 2.40])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.endingBalanceTime == atStartOfDay(endDate.minusDays(1))
  }

  def "keeps the end when one of the funds is valued at a price not stored yet"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start,
        [balance("1", 1000.0, 1.10), balance("2", 500.0, 2.00)],
        [balance("1", 1210.0, 1.21), balance("2", 650.0, 2.60)])
    pricesOf("1", ["2019-09-30": 1.10, (endDate.minusDays(1).toString()): 1.21])
    pricesOf("2", ["2019-09-30": 2.00, (endDate.minusDays(1).toString()): 2.40])

    when:
    AccountOverview accountOverview = accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    accountOverview.endingBalanceTime == endTime
  }

  def "does not let a fund of another pillar date the balances"() {
    when:
    accountOverviewProvider.getAccountOverview(person, startTime, endTime, pillar)

    then:
    0 * fundValueProvider.getLatestValue("3", _)
  }

  def "does not let a fund without money date the balances"() {
    given:
    LocalDate start = LocalDate.parse("2019-10-01")
    statementFrom(start,
        [balance("1", 1000.0, 1.10), balance("2", 0.0, 2.00)],
        [balance("1", 1210.0, 1.21), balance("2", 0.0, 2.40)])

    when:
    accountOverviewProvider.getAccountOverview(person, atStartOfDay(start), endTime, pillar)

    then:
    0 * fundValueProvider.getLatestValue("2", _)
  }

  private void statementFrom(LocalDate start, List<CashFlow> opening, List<CashFlow> closing) {
    episService.getCashFlowStatement(person, start, endDate) >> CashFlowStatement.builder()
        .startBalance(opening.collectEntries { [(it.isin): it] })
        .endBalance(closing.collectEntries { [(it.isin): it] })
        .transactions([])
        .build()
  }

  private void pricesOf(String isin, Map<String, BigDecimal> prices) {
    fundValueProvider.getLatestValue(isin, _ as LocalDate) >> { String key, LocalDate date ->
      def latest = prices.keySet().collect { LocalDate.parse(it) }.findAll { !it.isAfter(date) }.max()
      latest ? Optional.of(aFundValue(key, latest, prices[latest.toString()])) : Optional.empty()
    }
  }

  private static CashFlow balance(String isin, BigDecimal amount, BigDecimal unitPrice) {
    return CashFlow.builder().isin(isin).amount(amount).nav(unitPrice).currency(EUR)
        .time(Instant.parse("2001-01-02T00:00:00Z")).build()
  }

  private static Instant atStartOfDay(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant()
  }

  private boolean verifyTimeCloseToNow(Instant time) {
        return time.epochSecond > (clock.instant().epochSecond - 100)
    }
}
