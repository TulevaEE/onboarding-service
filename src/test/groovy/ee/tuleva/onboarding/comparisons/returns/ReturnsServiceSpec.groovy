package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX
import static ee.tuleva.onboarding.currency.Currency.EUR

class ReturnsServiceSpec extends Specification {

  def returnProvider1 = Mock(ReturnProvider)
  def returnProvider2 = Mock(ReturnProvider)
  def fundValueRepository = Mock(FundValueRepository)
  def returnsService = new ReturnsService([returnProvider1, returnProvider2], fundValueRepository)

  def "can get returns from multiple providers"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 2

    def (return1, returns1) = sampleReturns1(fromDate)
    def (return2, returns2) = sampleReturns2(fromDate)

    returnProvider1.getReturns(person, startTime, pillar) >> returns1
    returnProvider2.getReturns(person, startTime, pillar) >> returns2
    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getKeys() >> [return2.key]
    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(return1.from)
    fundValueRepository.findEarliestDateForKey(return2.key) >> Optional.of(return2.from)

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key, return2.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2]
    }
  }

  def "works with null keys"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 2

    def (return1, returns1) = sampleReturns1(fromDate)
    def (return2, returns2) = sampleReturns2(fromDate)

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
    }
  }

  def "can filter a single return from a return provider that provides many returns"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    def pillar = 2

    def (return1) = sampleReturns1(fromDate)
    def (return2) = sampleReturns2(fromDate)

    def allReturns = Returns.builder()
        .returns([return1, return2])
        .build()

    returnProvider1.getReturns(person, startTime, pillar) >> allReturns
    returnProvider2.getReturns(person, startTime, pillar) >> []
    returnProvider1.getKeys() >> [return1.key, return2.key]
    returnProvider2.getKeys() >> []
    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(return1.from)
    fundValueRepository.findEarliestDateForKey(return2.key) >> Optional.of(return2.from)

    when:
    def theReturns = returnsService.get(person, fromDate, [return1.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1]
    }
  }

  def "adjusts fromDate according to data availability"() {
    given:
        def person = samplePerson()
        LocalDate originalFromDate = LocalDate.parse("2020-01-01")
        LocalDate adjustedFromDate = LocalDate.parse("2020-02-01")
        def keys = [UnionStockIndexRetriever.KEY, EPIFundValueRetriever.KEY]

        fundValueRepository.findEarliestDateForKey(UnionStockIndexRetriever.KEY) >> Optional.of(adjustedFromDate)
        fundValueRepository.findEarliestDateForKey(EPIFundValueRetriever.KEY) >> Optional.of(adjustedFromDate)

        def (return1, returns1) = sampleReturns1(adjustedFromDate)
        def (return2, returns2) = sampleReturns2(adjustedFromDate)

        returnProvider1.getReturns(person, adjustedFromDate.atStartOfDay().toInstant(ZoneOffset.UTC), 2) >> returns1
        returnProvider2.getReturns(person, adjustedFromDate.atStartOfDay().toInstant(ZoneOffset.UTC), 2) >> returns2
        returnProvider1.getKeys() >> [return1.key]
        returnProvider2.getKeys() >> [return2.key]

    when:
        Returns returns = returnsService.get(person, originalFromDate, keys)

    then:
        returns.returns.size() == 2
        returns.returns.containsAll([return1, return2])
        returns.from == adjustedFromDate
  }

  def "uses original fromDate when data availability does not require adjustment"() {
    given:
        def person = samplePerson()
        LocalDate originalFromDate = LocalDate.parse("2020-01-01")
        def keys = [UnionStockIndexRetriever.KEY, EPIFundValueRetriever.KEY]

        fundValueRepository.findEarliestDateForKey(UnionStockIndexRetriever.KEY) >> Optional.of(originalFromDate)
        fundValueRepository.findEarliestDateForKey(EPIFundValueRetriever.KEY) >> Optional.of(originalFromDate)

        def (return1, returns1) = sampleReturns1(originalFromDate)
        def (return2, returns2) = sampleReturns2(originalFromDate)

        returnProvider1.getReturns(person, originalFromDate.atStartOfDay().toInstant(ZoneOffset.UTC), 2) >> returns1
        returnProvider2.getReturns(person, originalFromDate.atStartOfDay().toInstant(ZoneOffset.UTC), 2) >> returns2
        returnProvider1.getKeys() >> [return1.key]
        returnProvider2.getKeys() >> [return2.key]

    when:
        Returns returns = returnsService.get(person, originalFromDate, keys)

    then:
        returns.returns.size() == 2
        returns.returns.containsAll([return1, return2])
        returns.from == originalFromDate
  }

  private def sampleReturns1(LocalDate fromDate) {
    def return1 = Return.builder()
        .key(UnionStockIndexRetriever.KEY)
        .type(INDEX)
        .rate(0.0123)
        .amount(123.45)
        .paymentsSum(345.67)
        .currency(EUR)
        .from(fromDate)
        .build()

    def returns = Returns.builder()
        .returns([return1])
        .build()

    return [return1, returns]
  }

  private def sampleReturns2(LocalDate fromDate) {
    def return2 = Return.builder()
        .key(EPIFundValueRetriever.KEY)
        .type(FUND)
        .rate(0.0234)
        .amount(234.56)
        .paymentsSum(456.78)
        .currency(EUR)
        .from(fromDate)
        .build()

    def returns =  Returns.builder()
        .returns([return2])
        .build()

    return [return2, returns]
  }
}
