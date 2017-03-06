package ee.tuleva.onboarding.user

import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.Validation
import java.time.Instant

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
				.memberNumber(memberNumber)
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
		firstName | lastName   | personalCode   | createdDate                           | updatedDate                           | memberNumber | email              | phone     | active || propertyName   | message
		" "       | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | "erko@risthein.ee" | "5555555" | true   || "firstName"    | "may not be empty"
		"Erko"    | " "        | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | "erko@risthein.ee" | "5555555" | true   || "lastName"     | "may not be empty"
		"Erko"    | "Risthein" | "385010100029" | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | "erko@risthein.ee" | "5555555" | true   || "personalCode" | "size must be between 11 and 11"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2099-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | "erko@risthein.ee" | "5555555" | true   || "createdDate"  | "must be in the past"
		"Erko"    | "Risthein" | "38501010002"  | null                                  | Instant.parse("2017-01-31T10:06:01Z") | 3000         | "erko@risthein.ee" | "5555555" | true   || "createdDate"  | "may not be null"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | null                                  | 3000         | "erko@risthein.ee" | "5555555" | true   || "updatedDate"  | "may not be null"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | null         | "erko@risthein.ee" | "5555555" | true   || "memberNumber" | "may not be null"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | null               | "5555555" | true   || "email"        | "may not be null"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | " "                | "5555555" | true   || "email"        | "not a well-formed email address"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | Instant.parse("2017-01-31T10:06:01Z") | 3000         | "erko@risthein.ee" | "5555555" | null   || "active"       | "may not be null"
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
				.memberNumber(1)
				.active(true)
				.build()
	}
}
