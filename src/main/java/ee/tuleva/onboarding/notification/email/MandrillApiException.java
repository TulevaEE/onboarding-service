package ee.tuleva.onboarding.notification.email;

import lombok.Getter;

@Getter
class MandrillApiException extends RuntimeException {

  private static final String UNKNOWN_MESSAGE = "Unknown_Message";

  private final MandrillError error;

  MandrillApiException(String path, MandrillError error) {
    super(
        "Mandrill refused the request: path=%s, httpStatus=%d, code=%s, errorName=%s, errorMessage=%s"
            .formatted(path, error.httpStatus(), error.code(), error.name(), error.message()));
    this.error = error;
  }

  boolean isUnknownMessage() {
    return UNKNOWN_MESSAGE.equals(error.name());
  }
}
