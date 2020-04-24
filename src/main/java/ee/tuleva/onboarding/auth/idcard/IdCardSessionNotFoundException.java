package ee.tuleva.onboarding.auth.idcard;

public class IdCardSessionNotFoundException extends RuntimeException {
  public IdCardSessionNotFoundException() {
    super("ID-card session was not found.");
  }
}
