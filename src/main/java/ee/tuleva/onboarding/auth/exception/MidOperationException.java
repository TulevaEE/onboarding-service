package ee.tuleva.onboarding.auth.exception;

import java.util.List;

public class MidOperationException extends RuntimeException {

  private String message;

  public MidOperationException(String message) {
    this.message = message;
  }

  public MidOperationException(String message, Throwable cause) {
    super(cause);
    this.message = message + " Cause: " + cause.getMessage();
  }

  public MidOperationException(List<String> errors) {
    this.message = "MID service returned validation errors: " + String.join(", ", errors);
  }

  public String getMessage() {
    return message;
  }
}
