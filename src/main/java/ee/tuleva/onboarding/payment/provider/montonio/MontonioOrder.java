package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

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
  private BigDecimal amount;
  private Currency currency;
  private final String method = "paymentInitiation";
  private MontonioPaymentMethodOptions methodOptions;

}

@Builder
class MontonioPaymentMethodOptions {
  private String preferredProvider;
  private final String preferredCountry = "EE";
  private String preferredLocale;
}
