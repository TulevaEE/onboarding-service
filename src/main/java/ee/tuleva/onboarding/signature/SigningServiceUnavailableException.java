package ee.tuleva.onboarding.signature;

public class SigningServiceUnavailableException extends RuntimeException {

  private final String causeName;

  public SigningServiceUnavailableException(Throwable outage) {
    this(outage, outage);
  }

  SigningServiceUnavailableException(Throwable failure, Throwable outage) {
    super("Signing service unavailable: outage=" + outage, failure);
    this.causeName = outage.getClass().getSimpleName();
  }

  String causeName() {
    return causeName;
  }
}
