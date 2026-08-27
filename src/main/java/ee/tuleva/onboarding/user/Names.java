package ee.tuleva.onboarding.user;

import java.util.Locale;
import org.apache.commons.lang3.text.WordUtils;

public class Names {

  private static final Locale ESTONIAN = Locale.of("et");

  public static String formatted(String name) {
    if (name == null || name.isBlank()) {
      return name;
    }
    if (!name.equals(name.toUpperCase(ESTONIAN))) {
      return name;
    }
    return WordUtils.capitalizeFully(name, ' ', '-', '\'');
  }
}
