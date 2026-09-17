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
    // Montonio only uses this to prefill the payer's name; the money routes by the description.
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
