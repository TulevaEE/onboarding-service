package ee.tuleva.onboarding.auth.idcard.exception;

public class IdCardSessionNotFoundException extends RuntimeException {
  public IdCardSessionNotFoundException() {
    super("ID-card session was not found.");
  }
}
