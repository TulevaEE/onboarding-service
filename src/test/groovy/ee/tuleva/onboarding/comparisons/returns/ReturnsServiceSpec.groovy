package ee.tuleva.onboarding.comparisons.returns


import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider
import ee.tuleva.onboarding.time.ClockHolder
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import static java.time.temporal.ChronoUnit.DAYS

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.*
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo
import static java.util.Arrays.asList
import static java.util.Collections.singletonList

class ReturnsServiceSpec extends Specification {

  def returnProvider1 = Mock(ReturnProvider)
  def returnProvider2 = Mock(ReturnProvider)
  AccountOverviewProvider accountOverviewProvider = Mock(AccountOverviewProvider)
  def returnsService = new ReturnsService([returnProvider1, returnProvider2], accountOverviewProvider)

  def "can get returns from multiple providers"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 2

    def return1 = Return.builder()
        .key(UnionStockIndexRetriever.KEY)
        .type(INDEX)
        .value(0.0123)
        .build()

    def returns1 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return1))
        .build()

    def return2 = Return.builder()
        .key(EPIFundValueRetriever.KEY)
        .type(INDEX)
        .value(0.0234)
        .build()

    def returns2 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return2))
        .build()

    returnProvider1.getReturns(person, startTime, pillar) >> returns1
    returnProvider2.getReturns(person, startTime, pillar) >> returns2
    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getKeys() >> [return2.key]

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key, return2.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2]
      notEnoughHistory == false
    }
  }

  def "respond with not enough history if third pillar returns have less than a year of history"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2000-01-01")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 3

    def return1 = Return.builder()
        .key(THIRD_PILLAR)
        .type(INDEX)
        .value(0.0123)
        .build()

    def returns1 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return1))
        .build()


    returnProvider1.getReturns(person, startTime, pillar) >> returns1
    returnProvider1.getKeys() >> [return1.key]

    def overview = new AccountOverview([
        new Transaction(BigDecimal.ONE, aYearAgo().plus(1, DAYS))
    ], 0.0, BigDecimal.TEN, startTime, Instant.now(ClockHolder.clock()), 3)
    accountOverviewProvider.getAccountOverview(person, aYearAgo().truncatedTo(DAYS), 3) >> overview

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == null
      notEnoughHistory == true
    }
  }

  def "respond returns when there is more than a year worth of history for third pillar"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2000-01-01")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 3

    def return1 = Return.builder()
        .key(THIRD_PILLAR)
        .type(INDEX)
        .value(0.0123)
        .build()

    def returns1 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return1))
        .build()

    def return2 = Return.builder()
        .key(EPIFundValueRetriever.KEY)
        .type(INDEX)
        .value(0.0234)
        .build()

    def returns2 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return2))
        .build()

    returnProvider1.getReturns(person, startTime, pillar) >> returns1
    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getReturns(person, startTime, pillar) >> returns2
    returnProvider2.getKeys() >> [return2.key]

    def overview = new AccountOverview([
        new Transaction(BigDecimal.ONE, aYearAgo().minus(1, DAYS))
    ], BigDecimal.ONE, BigDecimal.TEN, startTime, Instant.now(ClockHolder.clock()), 3)
    accountOverviewProvider.getAccountOverview(person, aYearAgo().truncatedTo(DAYS), 3) >> overview

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key, return2.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2]
      notEnoughHistory == false
    }
  }

  def "respond returns when there is more than a year worth of history for third pillar, even if there are no transactions within past 1 year"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.ofInstant(aYearAgo(), ZoneOffset.UTC)
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 3

    def return1 = Return.builder()
        .key(THIRD_PILLAR)
        .type(INDEX)
        .value(0.0123)
        .build()

    def returns1 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return1))
        .build()

    def return2 = Return.builder()
        .key(EPIFundValueRetriever.KEY)
        .type(INDEX)
        .value(0.0234)
        .build()

    def returns2 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return2))
        .build()

    returnProvider1.getReturns(person, startTime, pillar) >> returns1
    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getReturns(person, startTime, pillar) >> returns2
    returnProvider2.getKeys() >> [return2.key]

    def overview = new AccountOverview([
    ], 100.0, 120.0, startTime, Instant.now(ClockHolder.clock()), 3)
    accountOverviewProvider.getAccountOverview(person, startTime, 3) >> overview

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key, return2.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2]
      notEnoughHistory == false
    }
  }

  def "works with null keys"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 2

    def return1 = Return.builder()
        .key(UnionStockIndexRetriever.KEY)
        .type(INDEX)
        .value(0.0123)
        .build()

    def returns1 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return1))
        .build()

    def return2 = Return.builder()
        .key(EPIFundValueRetriever.KEY)
        .type(INDEX)
        .value(0.0234)
        .build()

    def returns2 = Returns.builder()
        .from(fromDate)
        .returns(singletonList(return2))
        .build()

    returnProvider1.getReturns(person, startTime, pillar) >> returns1
    returnProvider2.getReturns(person, startTime, pillar) >> returns2
    returnProvider1.getKeys() >> return1.key
    returnProvider2.getKeys() >> return2.key

    when:
    def theReturns = returnsService.get(person, fromDate, null)

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2]
      notEnoughHistory == false
    }
  }

  def "can filter a single return from a return provider that provides many returns"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 2

    def return1 = Return.builder()
        .key("EE123")
        .type(FUND)
        .value(0.0123)
        .build()

    def return2 = Return.builder()
        .key("EE234")
        .type(FUND)
        .value(0.0234)
        .build()

    def allReturns = Returns.builder()
        .from(fromDate)
        .returns(asList(return1, return2))
        .build()

    returnProvider1.getReturns(person, startTime, pillar) >> allReturns
    returnProvider2.getReturns(person, startTime, pillar) >> []
    returnProvider1.getKeys() >> [return1.key, return2.key]
    returnProvider2.getKeys() >> []

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1]
      notEnoughHistory == false
    }
  }
}
