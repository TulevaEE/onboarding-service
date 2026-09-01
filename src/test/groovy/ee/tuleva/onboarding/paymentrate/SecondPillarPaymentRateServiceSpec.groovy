package ee.tuleva.onboarding.paymentrate

import ee.tuleva.onboarding.auth.principal.Person
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class SecondPillarPaymentRateServiceSpec extends Specification {

  def persistedPaymentRates = Mock(PersistedPaymentRates)
  def service = new SecondPillarPaymentRateService(persistedPaymentRates)

  def "getPaymentRates returns current and pending rates from persisted rates"() {
    given:
        Person person = samplePerson()
        persistedPaymentRates.forPerson(person) >> new PersistedPaymentRates.RatePair(6, 4)

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 6
        rates.pending.get() == 4
  }

  def "getPaymentRates returns only current rate when no pending"() {
    given:
        Person person = samplePerson()
        persistedPaymentRates.forPerson(person) >> new PersistedPaymentRates.RatePair(6, null)

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 6
        rates.pending == Optional.empty()
  }

  def "getPaymentRates defaults to 2 when persisted rates are null"() {
    given:
        Person person = samplePerson()
        persistedPaymentRates.forPerson(person) >> new PersistedPaymentRates.RatePair(null, null)

    when:
        PaymentRates rates = service.getPaymentRates(person)

    then:
        rates.current == 2
        rates.pending == Optional.empty()
  }

}
