package ee.tuleva.onboarding.payment.provider;

import lombok.Data;

@Data
class PaymentProviderBank {
  String accessKey;
  String secretKey;
  String bic;
}
