package ee.tuleva.onboarding.user.response

import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.user.User
import spock.lang.Specification
import spock.lang.Unroll

class UserResponseSpec extends Specification {

  @Unroll
  def "fromAuthenticatedPerson correctly capitalizes names: #firstName #lastName"() {
    given:
    def user = User.builder()
        .firstName(firstName)
        .lastName(lastName)
        .build()
    def contactDetails = new UserPreferences()

    when:
    def userResponse = UserResponse.fromUser(user, contactDetails)

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
