package ee.tuleva.onboarding.country;

import java.util.Locale;
import java.util.Set;

public final class EuropeanEconomicArea {

  static final Set<String> COUNTRY_CODES =
      Set.of(
          "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT",
          "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "NO", "IS", "LI");

  private EuropeanEconomicArea() {}

  public static boolean includes(String countryCode) {
    return COUNTRY_CODES.contains(countryCode.toUpperCase(Locale.ROOT));
  }
}
