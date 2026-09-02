package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.exception.SessionNotFoundException
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException
import ee.sk.smartid.exception.permanent.ServerMaintenanceException
import ee.sk.smartid.exception.permanent.SmartIdClientException
import ee.sk.smartid.exception.useraccount.CertificateLevelMismatchException
import ee.sk.smartid.exception.useraccount.DocumentUnusableException
import ee.sk.smartid.exception.useraccount.RequiredInteractionNotSupportedByAppException
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException
import ee.sk.smartid.exception.useraction.SessionTimeoutException
import ee.sk.smartid.exception.useraction.UserRefusedDisplayTextAndPinException
import ee.sk.smartid.exception.useraction.UserRefusedException
import ee.sk.smartid.exception.useraction.UserSelectedWrongVerificationCodeException
import ee.tuleva.onboarding.error.response.ErrorsResponse
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.smartid.SmartIdLoginError.*

class SmartIdLoginErrorSpec extends Specification {

  @Unroll
  def "maps #exception.class.simpleName to #error"() {
    expect:
    SmartIdLoginError.of(exception) == error

    where:
    exception                                             | error
    new UserRefusedException()                            | USER_REFUSED
    new UserRefusedDisplayTextAndPinException()           | USER_REFUSED
    new UserSelectedWrongVerificationCodeException()      | USER_REFUSED
    new SessionTimeoutException()                         | TIMEOUT
    new SessionNotFoundException()                        | TIMEOUT
    new UserAccountNotFoundException()                    | ACCOUNT_NOT_FOUND
    new DocumentUnusableException()                       | ACCOUNT_NOT_FOUND
    new CertificateLevelMismatchException()               | VALIDATION_FAILED
    new UnprocessableSmartIdResponseException("invalid")  | VALIDATION_FAILED
    new RequiredInteractionNotSupportedByAppException()   | TECHNICAL_ERROR
    new ServerMaintenanceException()                      | TECHNICAL_ERROR
    new SmartIdClientException("client")                  | TECHNICAL_ERROR
    new RuntimeException("boom")                          | TECHNICAL_ERROR
  }

  def "turns into a single error response with the code and message"() {
    expect:
    USER_REFUSED.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.user.refused", "Smart ID User refused")
    TIMEOUT.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.timeout", "Smart ID timed out waiting for the user")
    ACCOUNT_NOT_FOUND.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.account.not.found", "Smart ID user account not found")
    VALIDATION_FAILED.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.validation.failed", "Smart ID validation failed")
    TECHNICAL_ERROR.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.technical.error", "Smart ID technical error")
  }
}
