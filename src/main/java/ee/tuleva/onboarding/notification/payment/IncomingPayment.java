package ee.tuleva.onboarding.notification.payment;

import ee.tuleva.onboarding.notification.payment.validator.ValidMacCode;
import lombok.*;

@Data
@ValidMacCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class IncomingPayment {

  private String json;
  private String mac;
}
