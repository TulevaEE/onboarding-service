package ee.tuleva.onboarding.investment.event;

public class JobBusyException extends RuntimeException {

  public JobBusyException(String message) {
    super(message);
  }
}
