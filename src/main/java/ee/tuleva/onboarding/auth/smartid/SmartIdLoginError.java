package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

import ee.sk.smartid.exception.SessionNotFoundException;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.exception.UserAccountException;
import ee.sk.smartid.exception.useraccount.CertificateLevelMismatchException;
import ee.sk.smartid.exception.useraccount.RequiredInteractionNotSupportedByAppException;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.exception.useraction.UserSelectedWrongVerificationCodeException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SmartIdLoginError {
  USER_REFUSED("smart.id.user.refused", "Smart ID User refused"),
  TIMEOUT("smart.id.timeout", "Smart ID timed out waiting for the user"),
  ACCOUNT_NOT_FOUND("smart.id.account.not.found", "Smart ID user account not found"),
  VALIDATION_FAILED("smart.id.validation.failed", "Smart ID validation failed"),
  UNSUPPORTED_COUNTRY(
      "smart.id.unsupported.country", "Only Estonian Smart ID accounts are supported"),
  TECHNICAL_ERROR("smart.id.technical.error", "Smart ID technical error");

  private final String code;
  private final String message;

  public static SmartIdLoginError of(Exception exception) {
    return switch (exception) {
      case UnsupportedSmartIdCountryException _ -> UNSUPPORTED_COUNTRY;
      case UserRefusedException _ -> USER_REFUSED;
      case UserSelectedWrongVerificationCodeException _ -> USER_REFUSED;
      case SessionTimeoutException _ -> TIMEOUT;
      case SessionNotFoundException _ -> TIMEOUT;
      case CertificateLevelMismatchException _ -> VALIDATION_FAILED;
      case RequiredInteractionNotSupportedByAppException _ -> TECHNICAL_ERROR;
      case UserAccountException _ -> ACCOUNT_NOT_FOUND;
      case UnprocessableSmartIdResponseException _ -> VALIDATION_FAILED;
      default -> TECHNICAL_ERROR;
    };
  }

  public ErrorsResponse toErrorsResponse() {
    return ofSingleError(code, message);
  }
}
