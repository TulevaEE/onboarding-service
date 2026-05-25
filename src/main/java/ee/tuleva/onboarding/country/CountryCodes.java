package ee.tuleva.onboarding.country;

import static java.util.Locale.IsoCountryCode.PART1_ALPHA2;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

import jakarta.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

public final class CountryCodes {

  private static final Map<String, String> ALPHA3_TO_ALPHA2 =
      Locale.getISOCountries(PART1_ALPHA2).stream()
          .filter(alpha2 -> iso3Of(alpha2) != null)
          .collect(toUnmodifiableMap(CountryCodes::iso3Of, identity()));

  private CountryCodes() {}

  public static @Nullable String toAlpha2(@Nullable String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    var alpha3 = code.strip().toUpperCase(Locale.ROOT);
    return ALPHA3_TO_ALPHA2.getOrDefault(alpha3, code);
  }

  private static @Nullable String iso3Of(String alpha2) {
    try {
      return new Locale.Builder().setRegion(alpha2).build().getISO3Country();
    } catch (MissingResourceException e) {
      return null;
    }
  }
}
