package ee.tuleva.onboarding.hackathon;

public abstract class HackathonException extends RuntimeException {

  private final String errorCode;

  protected HackathonException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
