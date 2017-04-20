package ee.tuleva.onboarding.user.response

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class UserResponseTest extends Specification {

  @Unroll
  def "fromAuthenticatedPerson correctly capitalizes names: #firstName #lastName"() {
    given:
    def authenticatedPerson = AuthenticatedPerson.builder()
        .firstName(firstName)
        .lastName(lastName)
        .user(sampleUser())
        .build()

    when:
    def userResponse = UserResponse.fromAuthenticatedPerson(authenticatedPerson)

    then:
    userResponse.firstName == responseFirstName
    userResponse.lastName == responseLastName

    where:
    firstName   | lastName   || responseFirstName | responseLastName
    "ERKO"      | "RISTHEIN" || "Erko"            | "Risthein"
    "erko"      | "risthein" || "Erko"            | "Risthein"
    "ERKO ERKO" | "RISTHEIN" || "Erko Erko"       | "Risthein"
    "erko-erko" | "risthein" || "Erko-Erko"       | "Risthein"
  }

}
