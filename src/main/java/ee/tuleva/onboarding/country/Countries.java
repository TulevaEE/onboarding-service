package ee.tuleva.onboarding.country;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Countries {

  private Countries() {}

  public static Set<Country> of(@Nullable String... countryCodes) {
    return of(Arrays.asList(countryCodes));
  }

  public static Set<Country> of(@Nullable Country country) {
    return country == null ? Set.of() : of(country.getCountryCode());
  }

  public static Set<Country> of(Collection<@Nullable String> countryCodes) {
    return countryCodes.stream()
        .filter(code -> code != null && !code.isBlank())
        .map(Country::new)
        .collect(toUnmodifiableSet());
  }
}
