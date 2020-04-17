package ee.tuleva.onboarding.auth.exception;

public class IdCardSessionNotFoundException extends RuntimeException {
  public IdCardSessionNotFoundException() {
    super("ID-card session was not found.");
  }
}
