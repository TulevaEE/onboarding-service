package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PersonImpl
import groovy.transform.ToString

class PersonFixture {

  public static Person samplePerson =
      PersonImpl.builder()
          .personalCode("38812121215")
          .firstName("Jordan")
          .lastName("Valdma")
          .build()

  static PersonImpl samplePerson() {
    return samplePerson
  }

  static String sampleToken = "123"

}
