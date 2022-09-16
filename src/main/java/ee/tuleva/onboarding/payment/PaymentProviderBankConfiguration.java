package ee.tuleva.onboarding.payment;

import lombok.Data;

@Data
public class PaymentProviderBankConfiguration {
  String accessKey;
  String secretKey;
  String bic;
}
