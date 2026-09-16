package ee.tuleva.onboarding.savings.fund.gift;

/** Raised when the caller is not currently representing the child they are asking a link for. */
public class NotAllowedToGiftForException extends RuntimeException {

  public NotAllowedToGiftForException(String childPersonalCode) {
    // The code is safe to log here: the caller already sent it, and the message never reaches the
    // response body.
    super("Not representing this child: childPersonalCode=" + childPersonalCode);
  }
}
