package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnCalculationParameters
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider
import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.*
import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR
import static ee.tuleva.onboarding.currency.Currency.EUR
import static java.time.ZoneOffset.UTC

class ReturnsServiceSpec extends Specification {

  def returnProvider1 = Mock(ReturnProvider)
  def returnProvider2 = Mock(ReturnProvider)
  def returnProvider3 = Mock(ReturnProvider)
  def fundValueRepository = Mock(FundValueRepository)
  def mandateDeadlineService = new MandateDeadlinesService(TestClockHolder.clock, new PublicHolidays())
  def returnsService = new ReturnsService([returnProvider1, returnProvider2, returnProvider3], fundValueRepository, mandateDeadlineService)

  def "can get returns from multiple providers"() {
    given:
    def person = samplePerson()
    def fromDate = LocalDate.parse("2019-08-28")
    def startTime = fromDate.plusDays(1).atStartOfDay().toInstant(UTC)
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

    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(return1.from)
    fundValueRepository.findEarliestDateForKey(return2.key) >> Optional.of(return2.from)
    fundValueRepository.findEarliestDateForKey(return3.key) >> Optional.of(return3.from)

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

    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(return1.from)
    fundValueRepository.findEarliestDateForKey(return2.key) >> Optional.of(return2.from)
    fundValueRepository.findEarliestDateForKey(return3.key) >> Optional.of(return3.from)

    when:
    def theReturns = returnsService.get(person, fromDate, endDate, [return1.key])

    then:
    with(theReturns) {
      from == fromDate
      returns == [return1]
    }
  }

  def "adjusts fromDate according to data availability and 2nd pillar mandate deadlines"() {
    given:
    def person = samplePerson()
    LocalDate originalFromDate = LocalDate.parse("2020-01-01")
    LocalDate earliestNavDate = LocalDate.parse("2020-02-01")
    Instant adjustedStartTime = Instant.parse("2020-05-01T00:00:00Z")
    LocalDate adjustedStartDate = LocalDate.parse("2020-05-01")
    LocalDate endDate = LocalDate.now()
    Instant endTime = endDate.atStartOfDay().toInstant(UTC)

    def (return1, returns1) = sampleReturns1(adjustedStartDate)
    def (return2, returns2) = sampleReturns2(adjustedStartDate)
    def (return3, returns3) = sampleReturns3(adjustedStartDate)


    returnProvider1.getReturns(new ReturnCalculationParameters(person, adjustedStartTime, endTime, 2, [return1.key])) >> returns1
    returnProvider2.getReturns(new ReturnCalculationParameters(person, adjustedStartTime, endTime, 2, [return2.key])) >> returns2
    returnProvider3.getReturns(new ReturnCalculationParameters(person, adjustedStartTime, endTime, 2, [return3.key])) >> returns3

    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getKeys() >> [return2.key]
    returnProvider3.getKeys() >> [return3.key]

    def keys = [return1.key, return2.key]

    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(earliestNavDate)
    fundValueRepository.findEarliestDateForKey(return2.key) >> Optional.of(earliestNavDate)

    when:
    Returns theReturns = returnsService.get(person, originalFromDate, endDate, keys)

    then:
    with(theReturns) {
      returns == [return1, return2]
      from == adjustedStartDate
    }
  }

  def "uses original fromDate when data availability does not require adjustment"() {
    given:
    def person = samplePerson()
    LocalDate originalFromDate = LocalDate.parse("2020-01-01")
    Instant fromTime = originalFromDate.plusDays(1).atStartOfDay().toInstant(UTC)
    LocalDate endDate = LocalDate.now()
    Instant endTime = endDate.atStartOfDay().toInstant(UTC)

    def (return1, returns1) = sampleReturns1(originalFromDate)
    def (return2, returns2) = sampleReturns2(originalFromDate)
    def (return3, returns3) = sampleReturns3(originalFromDate)

    returnProvider1.getReturns(new ReturnCalculationParameters(person, fromTime, endTime, 3, [return1.key])) >> returns1
    returnProvider2.getReturns(new ReturnCalculationParameters(person, fromTime, endTime, 3, [return2.key])) >> returns2
    returnProvider3.getReturns(new ReturnCalculationParameters(person, fromTime, endTime, 3, [return3.key])) >> returns3

    returnProvider1.getKeys() >> [return1.key]
    returnProvider2.getKeys() >> [return2.key]
    returnProvider3.getKeys() >> [return3.key]

    def keys = [return1.key, return2.key, return3.key]

    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(originalFromDate)
    fundValueRepository.findEarliestDateForKey(return2.key) >> Optional.of(originalFromDate)
    fundValueRepository.findEarliestDateForKey(return3.key) >> Optional.of(originalFromDate)

    when:
    Returns theReturns = returnsService.get(person, originalFromDate, endDate, keys)

    then:
    with(theReturns) {
      returns == [return1, return2, return3]
      from == originalFromDate
    }
  }

  def "revises the from time correctly"() {
    given:
    def fromDate = LocalDate.parse(from)
    def (return1, returns1) = sampleReturns1(fromDate)
    def keys = [return1.key]

    def earliestNavDate = LocalDate.parse(earliestNav)

    fundValueRepository.findEarliestDateForKey(return1.key) >> Optional.of(earliestNavDate)

    when:
    Instant revisedFromTime = returnsService.getRevisedFromTime(fromDate, keys, pillar)

    then:
    revisedFromTime == Instant.parse(expectedRevision + "T00:00:00Z")

    where:
    from         | earliestNav  | pillar || expectedRevision
    "2020-01-01" | "2020-02-01" | 2      || "2020-05-01" // transferMandateFulfillmentDate
    "2020-01-01" | "2020-02-01" | 3      || "2020-02-02" // earliestNavDate + 1 days

    "2020-02-01" | "2020-02-01" | 2      || "2020-05-01" // transferMandateFulfillmentDate
    "2020-02-01" | "2020-02-01" | 3      || "2020-02-02" // earliestNavDate + 1 days

    "2020-03-01" | "2020-02-01" | 2      || "2020-05-01" // transferMandateFulfillmentDate
    "2020-03-01" | "2020-02-01" | 3      || "2020-03-01" // fromDate

    "2020-05-01" | "2020-02-01" | 2      || "2020-05-01" // fromDate = transferMandateFulfillmentDate
    "2020-06-01" | "2020-02-01" | 2      || "2020-06-01" // fromDate
    "2020-06-01" | "2020-02-01" | 3      || "2020-06-01" // fromDate
  }

  def "revises the from time correctly with no comparison keys"() {
    given:
    def fromDate = LocalDate.parse(from)

    when:
    Instant revisedFromTime = returnsService.getRevisedFromTime(fromDate, [], pillar)

    then:
    revisedFromTime == Instant.parse(expectedRevision + "T00:00:00Z")

    where:
    from          | pillar || expectedRevision
    "2020-01-01"  | 2      || "2020-05-01" // transferMandateFulfillmentDate
    "2020-01-01"  | 3      || "2020-01-01" // fromDate
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
