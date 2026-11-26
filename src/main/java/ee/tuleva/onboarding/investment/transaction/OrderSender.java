package ee.tuleva.onboarding.investment.transaction;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record OrderSender(String email, String name) {

  private static final String TULEVA_DOMAIN = "@tuleva.ee";

  boolean hasTulevaAddress() {
    return email.endsWith(TULEVA_DOMAIN) && email.length() > TULEVA_DOMAIN.length();
  }
}
