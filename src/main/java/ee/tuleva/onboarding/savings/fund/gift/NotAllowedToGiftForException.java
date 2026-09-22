package ee.tuleva.onboarding.savings.fund.gift;

public class NotAllowedToGiftForException extends RuntimeException {

  public NotAllowedToGiftForException(String childPersonalCode) {
    super("Not representing this child: childPersonalCode=" + childPersonalCode);
  }
}
