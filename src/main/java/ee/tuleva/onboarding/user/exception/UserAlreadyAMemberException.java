package ee.tuleva.onboarding.user.exception;

public class UserAlreadyAMemberException extends RuntimeException {

  public UserAlreadyAMemberException(String message) {
    super(message);
  }
}
