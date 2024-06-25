package ee.tuleva.onboarding.payment.provider;

import lombok.Data;

@Data
public class PaymentProviderChannel {
  String accessKey;
  String secretKey;
  String bic;
}
