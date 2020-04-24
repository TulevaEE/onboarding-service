package ee.tuleva.onboarding.auth.mobileid;

public class MobileIdSessionNotFoundException extends RuntimeException {
  public MobileIdSessionNotFoundException() {
    super("Mobile-ID session was not found.");
  }
}
