package ee.tuleva.onboarding.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Bank {
  LUMINOR("paymentProviderLuminorConfiguration"),
  SEB("paymentProviderSebConfiguration"),
  SWEDBANK("paymentProviderSwedbankConfiguration"),
  LHV("paymentProviderLhvConfiguration");

  private final String beanName;
}
