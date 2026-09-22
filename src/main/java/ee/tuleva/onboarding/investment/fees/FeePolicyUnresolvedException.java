package ee.tuleva.onboarding.investment.fees;

import lombok.Getter;

@Getter
public class FeePolicyUnresolvedException extends IllegalStateException {

  private final String reason;

  public FeePolicyUnresolvedException(String reason) {
    super(reason);
    this.reason = reason;
  }
}
