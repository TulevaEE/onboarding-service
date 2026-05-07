package ee.tuleva.onboarding.notification.email;

public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message) {
    super(message);
  }
}
