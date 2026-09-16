package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Builder
public record MontonioOrder(
    String accessKey,
    String merchantReference,
    String returnUrl,
    String notificationUrl,
    BigDecimal grandTotal,
    Currency currency,
    long exp,
    MontonioPaymentMethod payment,
    // Absent when nobody was logged in to pay, which is how a gift link works. Montonio only uses
    // this to prefill the payer's name, and the money is routed by the description regardless.
    @Nullable MontonioBillingAddress billingAddress,
    String locale) {

  @Data
  @Builder
  public static class MontonioPaymentMethod {
    private final String method = "paymentInitiation";
    private BigDecimal amount;
    private Currency currency;
    private MontonioPaymentMethodOptions methodOptions;

    @Data
    @Builder
    public static class MontonioPaymentMethodOptions {
      private final String preferredCountry = "EE";
      private @Nullable String preferredProvider;
      private String preferredLocale;
      private String paymentDescription;
    }
  }

  @Builder
  public record MontonioBillingAddress(String firstName, String lastName) {}
}
