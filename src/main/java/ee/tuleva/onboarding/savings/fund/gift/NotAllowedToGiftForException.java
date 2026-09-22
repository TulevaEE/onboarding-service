package ee.tuleva.onboarding.savings.fund.gift;

public class NotAllowedToGiftForException extends RuntimeException {

  public NotAllowedToGiftForException(String parentPersonalCode, String childPersonalCode) {
    super(
        "Not representing this child: parentPersonalCode="
            + parentPersonalCode
            + ", childPersonalCode="
            + childPersonalCode);
  }
}
