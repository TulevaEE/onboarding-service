package ee.tuleva.onboarding.payment.provider.montonio;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MontonioPaymentMethodOptions {
  private final String preferredCountry = "EE";
  private String preferredProvider;
  private String preferredLocale;
  private String paymentDescription;
}
