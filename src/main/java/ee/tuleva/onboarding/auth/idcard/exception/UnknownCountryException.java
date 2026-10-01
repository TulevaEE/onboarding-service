package ee.tuleva.onboarding.auth.idcard.exception;

import org.jspecify.annotations.Nullable;

public class UnknownCountryException extends RuntimeException {
  public UnknownCountryException(@Nullable String country) {
    super("Unsupported ID-card country: country=" + country);
  }
}
