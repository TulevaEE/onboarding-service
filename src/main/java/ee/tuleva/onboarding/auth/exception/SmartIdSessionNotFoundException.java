package ee.tuleva.onboarding.auth.exception;

public class SmartIdSessionNotFoundException extends RuntimeException {
  public SmartIdSessionNotFoundException() {
    super("Smart-ID session was not found.");
  }
}
