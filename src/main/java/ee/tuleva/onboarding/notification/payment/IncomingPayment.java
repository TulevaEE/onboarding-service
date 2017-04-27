package ee.tuleva.onboarding.notification.payment;

import ee.tuleva.onboarding.notification.payment.validator.ValidMacCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@ValidMacCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomingPayment {

  private String json;
  private String mac;

}
