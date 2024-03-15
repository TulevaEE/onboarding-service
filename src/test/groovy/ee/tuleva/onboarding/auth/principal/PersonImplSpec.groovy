package ee.tuleva.onboarding.auth.principal

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class PersonImplSpec extends Specification {

  def "can create PersonImpl from Person"() {
    given:
    Person person = samplePerson()

    when:
    PersonImpl personImpl = new PersonImpl(person)

    then:
    personImpl.personalCode == person.personalCode
    personImpl.firstName == person.firstName
    personImpl.lastName == person.lastName
  }
}
