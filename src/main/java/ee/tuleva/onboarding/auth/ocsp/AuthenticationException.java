package ee.tuleva.onboarding.auth.ocsp;

import lombok.Getter;

public class AuthenticationException extends RuntimeException {
  public enum Code {
    INVALID_INPUT("Input parameters are in a wrong format"),
    USER_CERTIFICATE_MISSING("User certificate is missing"),
    UNABLE_TO_TEST_USER_CERTIFICATE("Unable to test user certificate"),
    REVOKED("User certificate is not valid (OCSP response REVOKED)."),
    UNKNOWN("User certificate status is unknown (OCSP response UNKNOWN)."),
    EXPIRED("User certificate is expired"),
    UNAUTHORIZED("Unauthorized access (Perhaps a subscription to the service is required first)"),
    INTERNAL_ERROR("Internal service error");

    private final String descriptionInEstonian;

    Code(String descriptionInEstonian) {
      this.descriptionInEstonian = descriptionInEstonian;
    }
  }

  @Getter private Code code;

  public AuthenticationException(Code code, String details, Throwable throwable) {
    super(code + ": " + details, throwable);
    this.code = code;
  }

  public AuthenticationException(Code code, String details) {
    super(code + ": " + details);
    this.code = code;
  }

  public AuthenticationException(Code code) {
    super(code.toString());
    this.code = code;
  }
}
