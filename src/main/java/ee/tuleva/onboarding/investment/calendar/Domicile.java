package ee.tuleva.onboarding.investment.calendar;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public enum Domicile {
  IRELAND("IE"),
  LUXEMBOURG("LU"),
  FRANCE("FR");

  private final String countryCode;

  /**
   * Resolves an ISO 3166-1 alpha-2 country code, as stored in {@code instrument_reference.country}.
   * Empty for a null, blank or unsupported code — the caller decides what to fall back to.
   */
  public static Optional<Domicile> forCountryCode(@Nullable String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return Optional.empty();
    }
    String normalized = countryCode.strip().toUpperCase();
    return Arrays.stream(values()).filter(it -> it.countryCode.equals(normalized)).findFirst();
  }
}
