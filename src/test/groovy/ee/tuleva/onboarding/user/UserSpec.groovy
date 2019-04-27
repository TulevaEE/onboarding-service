package ee.tuleva.onboarding.user

import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.Validation
import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class UserSpec extends Specification {

	def validatorFactory = Validation.buildDefaultValidatorFactory()
	def validator = validatorFactory.getValidator()

	def "validation passes for valid user"() {
		given:
		def user = validUser()

		when:
		def violations = validator.validate(user)

		then:
		violations.isEmpty()
	}

	@Unroll
	def "#propertyName #message"() {
		given:
		def user = User.builder()
				.firstName(firstName)
				.lastName(lastName)
				.personalCode(personalCode)
				.email(email)
				.phoneNumber(phone)
				.createdDate(createdDate)
				.updatedDate(updatedDate)
				.active(active)
				.build()

		when:
		def violations = validator.validate(user)

		then:
		violations.size() == 1
		def violation = violations.iterator().next()
		violation.propertyPath.toString() == propertyName
		violation.getMessage() == message

		where:
		firstName | lastName   | personalCode   | createdDate                           | updatedDate                           | email              | phone     | active || propertyName   | message
		"Erko"    | "Risthein" | "385010100029" | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | "erko@risthein.ee" | "5555555" | true   || "personalCode" | "{ee.tuleva.onboarding.user.personalcode.ValidPersonalCode.message}"
		"Erko"    | "Risthein" | "38501010001"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | "erko@risthein.ee" | "5555555" | true   || "personalCode" | "{ee.tuleva.onboarding.user.personalcode.ValidPersonalCode.message}"
		"Erko"    | "Risthein" | "38501010002"  | null                                  | Instant.parse("2017-01-31T10:06:01Z") | "erko@risthein.ee" | "5555555" | true   || "createdDate"  | "must not be null"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | null                                  | "erko@risthein.ee" | "5555555" | true   || "updatedDate"  | "must not be null"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | " "                | "5555555" | true   || "email"        | "must be a well-formed email address"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | "erko@risthein.ee" | "5555555" | null   || "active"       | "must not be null"
		"Erko"    | "Risthein" | "59001010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | "erko@risthein.ee" | "5555555" | true   || "age"          | "must be greater than or equal to 18" // ticking time bomb. test will start failing in a hundred years :trollface:
	}

	def cleanup() {
		validatorFactory.close()
	}

	private static User validUser() {
		return User.builder()
				.firstName("Erko")
				.lastName("Risthein")
				.personalCode("38501010002")
				.email("erko@risthein.ee")
				.phoneNumber("5555555")
				.createdDate(Instant.parse("2017-01-31T10:06:01Z"))
				.updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
				.active(true)
				.build()
	}

	def "user has a name when at least one of the names exist"() {
		when:
		def user = sampleUser().firstName(firstName).lastName(lastName).build()

		then:
		user.hasName() == hasName

		where:
		firstName | lastName || hasName
		null      | null     || false
		null      | "Smith"  || true
		"John"    | null     || true
		"John"    | "Smith"  || true
	}
}
