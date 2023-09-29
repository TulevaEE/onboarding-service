package ee.tuleva.onboarding.payment.provider;

import lombok.Data;

@Data
class PaymentProviderChannel {
  String accessKey;
  String secretKey;
  String bic;
}
