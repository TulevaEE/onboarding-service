package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.http.HttpStatus
import spock.lang.Specification

class AuthErrorHandlerSpec extends Specification {

  AuthErrorHandler handler = new AuthErrorHandler()

  def "handle AuthNotCompleteException returns OK response with specific message"() {
    when:
        def result = handler.handleErrors(new AuthNotCompleteException())

    then:
        result.statusCode == HttpStatus.OK
        result.body == [error: "AUTHENTICATION_NOT_COMPLETE", error_description: "Please keep polling."]
  }

  def "handle ExpiredJwtException returns UNAUTHORIZED response with expected error details"() {
    when:
        def result = handler.handleErrors(new ExpiredJwtException(null, null, "The token is expired."))

    then:
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == [error: "TOKEN_EXPIRED", error_description: "The token is expired."]
  }

  def "handle ExpiredRefreshJwtException returns FORBIDDEN response with expected error details"() {
    when:
        def result = handler.handleErrors(new ExpiredRefreshJwtException())

    then:
        result.statusCode == HttpStatus.FORBIDDEN
        result.body == [error: "REFRESH_TOKEN_EXPIRED", error_description: "The refresh token is expired."]
  }
}
