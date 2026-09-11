package ee.tuleva.onboarding.auth.smartid;

import java.util.Locale;
import java.util.MissingResourceException;
import org.jspecify.annotations.Nullable;

final class DeviceLinkLanguage {

  private static final String DEFAULT = "est";

  private DeviceLinkLanguage() {}

  static String of(@Nullable String language) {
    if (language == null || language.isBlank()) {
      return DEFAULT;
    }
    try {
      String iso3 = Locale.forLanguageTag(language).getISO3Language();
      return iso3.isEmpty() ? DEFAULT : iso3;
    } catch (MissingResourceException e) {
      return DEFAULT;
    }
  }
}
