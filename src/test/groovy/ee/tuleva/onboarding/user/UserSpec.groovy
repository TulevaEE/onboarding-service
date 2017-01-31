package ee.tuleva.onboarding.user

import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.Validation
import javax.validation.Validator
import javax.validation.ValidatorFactory
import java.time.Instant

class UserSpec extends Specification {

	private static ValidatorFactory validatorFactory
	private static Validator validator


	def setup() {
		validatorFactory = Validation.buildDefaultValidatorFactory()
		validator = validatorFactory.getValidator()
	}

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
				.createdDate(createdDate)
				.memberNumber(memberNumber)
				.build()

		when:
		def violations = validator.validate(user)

		then:
		violations.size() == 1
		def violation = violations.iterator().next()
		violation.propertyPath.toString() == propertyName
		violation.getMessage() == message

		where:
		firstName | lastName   | personalCode   | createdDate                           | memberNumber || propertyName   || message
		" "       | "Risthein" | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | 3000         || "firstName"    || "may not be empty"
		"Erko"    | " "        | "38501010002"  | Instant.parse("2017-01-31T10:06:01Z") | 3000         || "lastName"     || "may not be empty"
		"Erko"    | "Risthein" | "385010100029" | Instant.parse("2017-01-31T10:06:01Z") | 3000         || "personalCode" || "size must be between 11 and 11"
		"Erko"    | "Risthein" | "38501010002"  | Instant.parse("2099-01-31T10:06:01Z") | 3000         || "createdDate"  || "must be in the past"
		"Erko"    | "Risthein" | "38501010002"  | null                                  | 3000         || "createdDate"  || "may not be null"
	}

	def cleanup() {
		validatorFactory.close()
	}

	private static User validUser() {
		return User.builder()
				.firstName("Erko")
				.lastName("Risthein")
				.personalCode("38501010002")
				.createdDate(Instant.parse("2017-01-31T10:06:01Z"))
				.memberNumber(null)
				.build()
	}
}
