package ee.tuleva.onboarding.mandate.payment.rate;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PaymentRateCommand {
  @ValidPaymentRate private BigDecimal paymentRate;
}
