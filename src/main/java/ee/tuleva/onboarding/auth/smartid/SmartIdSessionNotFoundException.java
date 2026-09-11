package ee.tuleva.onboarding.auth.smartid;

public class SmartIdSessionNotFoundException extends RuntimeException {
  public SmartIdSessionNotFoundException() {
    this("Smart-ID session was not found.");
  }

  public SmartIdSessionNotFoundException(String message) {
    super(message);
  }
}
