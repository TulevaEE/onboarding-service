package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MontonioPaymentMethod {
  private final String method = "paymentInitiation";
  private BigDecimal amount;
  private Currency currency;
  private MontonioPaymentMethodOptions methodOptions;
}
