package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnCalculationParameters
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.*
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.SECOND_PILLAR
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR
import static ee.tuleva.onboarding.currency.Currency.EUR
import static java.time.ZoneOffset.UTC

class ReturnsServiceSpec extends Specification {

  def returnProvider1 = Mock(ReturnProvider)
  def returnProvider2 = Mock(ReturnProvider)
  def returnProvider3 = Mock(ReturnProvider)
  def returnsService = new ReturnsService([returnProvider1, returnProvider2, returnProvider3])

  def "can get returns from multiple providers"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.atStartOfDay().toInstant(UTC)
    def endDate = LocalDate.now()
    def endTime = endDate.atStartOfDay().toInstant(UTC)
    def pillar = 3

    def (return1, returns1) = sampleReturns1(fromDate)
    def (return2, returns2) = sampleReturns2(fromDate)
    def (return3, returns3) = sampleReturns3(fromDate)

    returnProvider1.getReturns(new ReturnCalculationParameters(person, startTime, endTime, pillar, [return1.key])) >> returns1
    returnProvider2.getReturns(new ReturnCalculationParameters(person, startTime, endTime, pillar, [return2.key])) >> returns2
    returnProvider3.getReturns(new ReturnCalculationParameters(person, startTime, endTime, pillar, [return3.key])) >> returns3

    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getKeys() >> [return2.key]
    returnProvider3.getKeys() >> [return3.key]

    when:
    def theReturns = returnsService.get(person, fromDate, endDate, [return1.key, return2.key, return3.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2, return3]
    }
  }

  def "works with null keys"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def endDate = LocalDate.now()

    def (return1, returns1) = sampleReturns1(fromDate)
    def (return2, returns2) = sampleReturns2(fromDate)
    def (return3, returns3) = sampleReturns3(fromDate)

    returnProvider1.getReturns(_ as ReturnCalculationParameters) >> returns1
    returnProvider2.getReturns(_ as ReturnCalculationParameters) >> returns2
    returnProvider3.getReturns(_ as ReturnCalculationParameters) >> returns3

    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getKeys() >> [return2.key]
    returnProvider3.getKeys() >> [return3.key]

    when:
    def theReturns = returnsService.get(person, fromDate, endDate, null)

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1, return2, return3]
    }
  }

  def "can filter a single return from a return provider that provides many returns"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def endDate = LocalDate.now()

    def (return1) = sampleReturns1(fromDate)
    def (return2) = sampleReturns2(fromDate)
    def (return3) = sampleReturns3(fromDate)

    def allReturns = Returns.builder()
        .returns([return1, return2, return3])
        .build()

    returnProvider1.getReturns(_ as ReturnCalculationParameters) >> allReturns
    returnProvider2.getReturns(_ as ReturnCalculationParameters) >> []
    returnProvider3.getReturns(_ as ReturnCalculationParameters) >> []

    returnProvider1.getKeys() >> [return1.key, return2.key, return3.key]
    returnProvider2.getKeys() >> []
    returnProvider3.getKeys() >> []

    when:
    def theReturns = returnsService.get(person, fromDate, endDate, [return1.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1]
    }
  }

  @Unroll
  def "asks for every return over exactly the requested period, whatever is asked for alongside it"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse(from)
    def endDate = LocalDate.parse("2026-08-22")
    def expectedParameters = new ReturnCalculationParameters(
        person, Instant.parse("${from}T00:00:00Z"), Instant.parse("2026-08-22T00:00:00Z"), pillar, keys)

    returnProvider1.getKeys() >> [SECOND_PILLAR, THIRD_PILLAR, UnionStockIndexRetriever.KEY, EpiIndex.EPI.key]
    returnProvider2.getKeys() >> []
    returnProvider3.getKeys() >> []

    when:
    returnsService.get(person, fromDate, endDate, keys)

    then:
    1 * returnProvider1.getReturns(expectedParameters) >> Returns.builder().returns([]).build()

    where:
    from         | keys                                                            | pillar
    "2020-03-01" | [SECOND_PILLAR, UnionStockIndexRetriever.KEY, EpiIndex.EPI.key] | 2
    "2019-10-14" | [THIRD_PILLAR, EpiIndex.EPI.key]                                | 3
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
        .to(LocalDate.now())
        .build()

    def returns = Returns.builder()
        .returns([return1])
        .build()

    return [return1, returns]
  }

  private def sampleReturns2(LocalDate fromDate) {
    def return2 = Return.builder()
        .key(EpiIndex.EPI.key)
        .type(FUND)
        .rate(0.0234)
        .amount(234.56)
        .paymentsSum(456.78)
        .currency(EUR)
        .from(fromDate)
        .to(LocalDate.now())
        .build()

    def returns = Returns.builder()
        .returns([return2])
        .build()

    return [return2, returns]
  }

  private def sampleReturns3(LocalDate fromDate) {
    def return3 = Return.builder()
        .key(THIRD_PILLAR)
        .type(PERSONAL)
        .rate(0.0345)
        .amount(345.67)
        .paymentsSum(567.89)
        .currency(EUR)
        .from(fromDate)
        .to(LocalDate.now())
        .build()

    def returns = Returns.builder()
        .returns([return3])
        .build()

    return [return3, returns]
  }
}
