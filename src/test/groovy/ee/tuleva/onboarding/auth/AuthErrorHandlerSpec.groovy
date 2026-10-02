package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.idcard.IdDocumentType
import ee.tuleva.onboarding.auth.idcard.exception.UnsupportedDocumentTypeException
import ee.tuleva.onboarding.auth.idcard.exception.UnknownCountryException
import ee.tuleva.onboarding.auth.principal.MinorCannotSelfAuthenticateException
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.auth.smartid.SmartIdSessionNotFoundException
import ee.tuleva.onboarding.error.response.ErrorsResponse
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.resilience.InvocationRejectedException
import org.springframework.http.HttpStatus
import spock.lang.Specification

class AuthErrorHandlerSpec extends Specification {

  AuthErrorHandler handler = new AuthErrorHandler()

  def "handle SmartIdSessionNotFoundException returns UNAUTHORIZED response with session-not-found details"() {
    when:
        def result = handler.handleAuthSessionNotFound(new SmartIdSessionNotFoundException())

    then:
        result.statusCode == HttpStatus.UNAUTHORIZED
        result.body == ErrorsResponse.ofSingleError("auth.session.not.found", "Smart-ID session was not found.")
  }

  def "handle UnsupportedDocumentTypeException returns BAD_REQUEST with a dedicated error code"() {
    when:
    def response = handler.handleUnsupportedDocumentType(
        new UnsupportedDocumentTypeException(IdDocumentType.E_RESIDENT_DIGITAL_ID_CARD))

    then:
    response.statusCode == HttpStatus.BAD_REQUEST
    response.body.errors[0].code == "id.card.document.type.not.allowed"
  }

  def "handle MinorCannotSelfAuthenticateException returns FORBIDDEN response with expected error details"() {
    when:
        def result = handler.handleErrors(new MinorCannotSelfAuthenticateException("38888888888"))

    then:
        result.statusCode == HttpStatus.FORBIDDEN
        result.body == [error: "MINOR_CANNOT_SELF_AUTHENTICATE", error_description: "Minor cannot self-authenticate: personalCode=38888888888"]
  }

  def "handle UnknownCountryException returns BAD_REQUEST rather than failing the request"() {
    when:
    def response = handler.handleUnknownCountry(new UnknownCountryException("LV"))
    then:
    response.statusCode == HttpStatus.BAD_REQUEST
    response.body.errors[0].code == "id.card.country.not.allowed"
  }

  def "handle InvocationRejectedException returns TOO_MANY_REQUESTS so the caller backs off"() {
    when:
    def response = handler.handleTooManyLogins(new InvocationRejectedException("limit reached", this))
    then:
    response.statusCode == HttpStatus.TOO_MANY_REQUESTS
    response.body.errors[0].code == "auth.too.many.requests"
  }

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
