package ee.tuleva.onboarding.user.response

import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.user.User
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDate
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class UserResponseSpec extends Specification {

  @Unroll
  def "fromAuthenticatedPerson correctly capitalizes names: #firstName #lastName"() {
    given:
    def user = User.builder()
        .firstName(firstName)
        .lastName(lastName)
        .build()
    def contactDetails = new ContactDetails()
    def paymentRates = new PaymentRates(2, 6)

    when:
    def userResponse = UserResponse.from(user, contactDetails, paymentRates)

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

  def "calculates age, retirement age and date of birth in the API response"() {
    given:
    def user = sampleUser().build()

    when:
    def userResponse = UserResponse.from(user)

    then:
    userResponse.age > 0
    userResponse.retirementAge == 65
    userResponse.dateOfBirth == LocalDate.parse("1988-12-12")
  }

  def "converts pending payment rate correctly"() {
    given:
        def user = User.builder()
            .firstName("firstName")
            .lastName("lastName")
            .build()
        def contactDetails = new ContactDetails()
        def paymentRates = new PaymentRates(currentPaymentRate, pendingPaymentRate)

    when:
        def userResponse = UserResponse.from(user, contactDetails, paymentRates)

    then:
        userResponse.secondPillarPaymentRates.current == responseCurrentPaymentRate
        userResponse.secondPillarPaymentRates.pending == responsePendingPaymentRate

    where:
        currentPaymentRate   | pendingPaymentRate   || responseCurrentPaymentRate | responsePendingPaymentRate
        2 | 4     || 2  | 4
        2 | null  || 2  | null
  }

  def "converts third pillar status correctly"() {
      given:
          def user = User.builder()
              .firstName("firstName")
              .lastName("lastName")
              .build()
          def contactDetails = new ContactDetails()
          contactDetails.setThirdPillarActive(isActive)
          contactDetails.setThirdPillarInitDate(initiatedDate)
          def paymentRates = new PaymentRates(2, 6)

      when:
          def userResponse = UserResponse.from(user, contactDetails, paymentRates)

      then:
          userResponse.thirdPillarActive == expectedIsActive

      where:
          isActive |  initiatedDate                              || expectedIsActive
          true     |  Instant.parse("2018-12-31T10:00:00Z")      || true
          false    |  Instant.parse("2018-12-31T10:00:00Z")      || true
          false    |  Instant.parse("2024-01-01T10:00:00Z")      || true
          true     |  null                                       || true
          false    |  null                                       || false
  }
}
