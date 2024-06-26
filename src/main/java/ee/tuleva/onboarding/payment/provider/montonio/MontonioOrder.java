package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;

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

@Builder
class MontonioPaymentMethod {
  private final String method = "paymentInitiation";
  private BigDecimal amount;
  private Currency currency;
  private MontonioPaymentMethodOptions methodOptions;
}

@Builder
class MontonioPaymentMethodOptions {
  private final String preferredCountry = "EE";
  private String preferredProvider;
  private String preferredLocale;
  private String paymentDescription;
}
