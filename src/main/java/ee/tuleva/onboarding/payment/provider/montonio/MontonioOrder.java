package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Builder
record MontonioOrder(
    String accessKey,
    String merchantReference,
    String returnUrl,
    String notificationUrl,
    BigDecimal grandTotal,
    Currency currency,
    long exp,
    MontonioPaymentMethod payment,
    MontonioBillingAddress billingAddress,
    String locale) {

  @Data
  @Builder
  static class MontonioPaymentMethod {
    private final String method = "paymentInitiation";
    private BigDecimal amount;
    private Currency currency;
    private MontonioPaymentMethodOptions methodOptions;

    @Data
    @Builder
    static class MontonioPaymentMethodOptions {
      private final String preferredCountry = "EE";
      private String preferredProvider;
      private String preferredLocale;
      private String paymentDescription;
    }
  }

  @Builder
  record MontonioBillingAddress(String firstName, String lastName) {}
}
