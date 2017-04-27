package ee.tuleva.onboarding.user.response

import ee.tuleva.onboarding.user.User
import spock.lang.Specification
import spock.lang.Unroll

class UserResponseTest extends Specification {

  @Unroll
  def "fromAuthenticatedPerson correctly capitalizes names: #firstName #lastName"() {
    given:
    def user = User.builder()
        .firstName(firstName)
        .lastName(lastName)
        .build()

    when:
    def userResponse = UserResponse.fromUser(user)

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
