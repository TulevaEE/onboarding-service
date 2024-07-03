package ee.tuleva.onboarding.payment.provider.montonio;

import lombok.Data;

@Data
public class MontonioPaymentChannel {
  String accessKey;
  String secretKey;
  String bic;
}
