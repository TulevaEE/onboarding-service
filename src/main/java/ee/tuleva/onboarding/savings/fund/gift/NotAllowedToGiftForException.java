package ee.tuleva.onboarding.savings.fund.gift;

import lombok.Getter;

@Getter
public class NotAllowedToGiftForException extends RuntimeException {

  private final String parentPersonalCode;
  private final String childPersonalCode;

  public NotAllowedToGiftForException(String parentPersonalCode, String childPersonalCode) {
    super(
        "Not representing this child: parentPersonalCode="
            + parentPersonalCode
            + ", childPersonalCode="
            + childPersonalCode);
    this.parentPersonalCode = parentPersonalCode;
    this.childPersonalCode = childPersonalCode;
  }
}
