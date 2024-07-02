package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MontonioOrder {
  private String accessKey;
  private String merchantReference;
  private String returnUrl;
  private String notificationUrl;
  private BigDecimal grandTotal;
  private Currency currency;
  private long exp;
  private MontonioPaymentMethod payment;
  private String locale;
}
