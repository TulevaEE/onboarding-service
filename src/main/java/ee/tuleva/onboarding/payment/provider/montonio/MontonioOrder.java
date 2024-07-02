package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
class MontonioOrder {
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

@Data
@Builder
class MontonioPaymentMethod {
  private final String method = "paymentInitiation";
  private BigDecimal amount;
  private Currency currency;
  private MontonioPaymentMethodOptions methodOptions;
}

@Data
@Builder
class MontonioPaymentMethodOptions {
  private final String preferredCountry = "EE";
  private String preferredProvider;
  private String preferredLocale;
  private String paymentDescription;
}
