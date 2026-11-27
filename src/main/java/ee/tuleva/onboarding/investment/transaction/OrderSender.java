package ee.tuleva.onboarding.investment.transaction;

import java.util.Locale;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record OrderSender(String email, String name) {

  private static final String TULEVA_DOMAIN = "@tuleva.ee";

  boolean hasTulevaAddress() {
    String address = email.toLowerCase(Locale.ROOT);
    return address.endsWith(TULEVA_DOMAIN) && address.length() > TULEVA_DOMAIN.length();
  }
}
