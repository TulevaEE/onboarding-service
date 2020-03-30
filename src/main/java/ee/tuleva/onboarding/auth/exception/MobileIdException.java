package ee.tuleva.onboarding.auth.exception;

import java.util.List;

public class MobileIdException extends RuntimeException {

  private String message;

  public MobileIdException(String message) {
    this.message = message;
  }

  public MobileIdException(String message, Throwable cause) {
    super(cause);
    this.message = message + " Cause: " + cause.getMessage();
  }

  public MobileIdException(List<String> errors) {
    this.message = "MID service returned validation errors: " + String.join(", ", errors);
  }

  public String getMessage() {
    return message;
  }
}
