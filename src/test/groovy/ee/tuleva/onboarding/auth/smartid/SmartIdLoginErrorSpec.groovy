package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.exception.SessionNotFoundException
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException
import ee.sk.smartid.exception.permanent.ServerMaintenanceException
import ee.sk.smartid.exception.permanent.SmartIdClientException
import ee.sk.smartid.exception.useraccount.CertificateLevelMismatchException
import ee.sk.smartid.exception.useraccount.DocumentUnusableException
import ee.sk.smartid.exception.useraccount.NoSuitableAccountOfRequestedTypeFoundException
import ee.sk.smartid.exception.useraccount.PersonShouldViewSmartIdPortalException
import ee.sk.smartid.exception.useraccount.RequiredInteractionNotSupportedByAppException
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException
import ee.sk.smartid.exception.useraccount.UserAccountUnusableException
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
    new UnsupportedSmartIdCountryException("LT")          | UNSUPPORTED_COUNTRY
    new SmartIdCertificateRevokedException("REVOKED")     | CERTIFICATE_REVOKED
    new SmartIdCertificateStatusUnavailableException("x") | TECHNICAL_ERROR
    new UserRefusedException()                            | USER_REFUSED
    new UserRefusedDisplayTextAndPinException()           | USER_REFUSED
    new UserSelectedWrongVerificationCodeException()      | WRONG_VERIFICATION_CODE
    new SessionTimeoutException()                         | TIMEOUT
    new SessionNotFoundException()                        | TIMEOUT
    new UserAccountNotFoundException()                    | ACCOUNT_NOT_FOUND
    new DocumentUnusableException()                       | ACCOUNT_UNUSABLE
    new UserAccountUnusableException()                    | ACCOUNT_UNUSABLE
    new NoSuitableAccountOfRequestedTypeFoundException()  | ACCOUNT_UNUSABLE
    new PersonShouldViewSmartIdPortalException()          | ACCOUNT_UNUSABLE
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
    ACCOUNT_UNUSABLE.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.account.unusable", "Smart ID account cannot be used until the person checks the Smart-ID app or portal")
    VALIDATION_FAILED.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.validation.failed", "Smart ID validation failed")
    UNSUPPORTED_COUNTRY.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.unsupported.country", "Only Estonian Smart ID accounts are supported")
    WRONG_VERIFICATION_CODE.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.wrong.verification.code", "Smart ID user chose the wrong verification code")
    CERTIFICATE_REVOKED.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.certificate.revoked", "Smart ID certificate is revoked or unknown to its issuer")
    TECHNICAL_ERROR.toErrorsResponse() == ErrorsResponse.ofSingleError("smart.id.technical.error", "Smart ID technical error")
  }
}
