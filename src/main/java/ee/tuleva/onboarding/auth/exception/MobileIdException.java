package ee.tuleva.onboarding.auth.exception;

import java.util.List;

public class MobileIdException extends RuntimeException {

  public MobileIdException(String message) {
    super(message);
  }

  public MobileIdException(String message, Throwable cause) {
    super(message, cause);
  }

  public MobileIdException(List<String> errors) {
    super("MID service returned validation errors: " + String.join(", ", errors));
  }
}
