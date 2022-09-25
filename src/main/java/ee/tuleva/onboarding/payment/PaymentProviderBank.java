package ee.tuleva.onboarding.payment;

import lombok.Data;

@Data
public class PaymentProviderBank {
  String accessKey;
  String secretKey;
  String bic;
}
