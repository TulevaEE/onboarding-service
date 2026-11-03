package ee.tuleva.onboarding.insurance;

import java.util.Locale;

final class ArteaContractNumber {

  private ArteaContractNumber() {}

  static String printed(String typed) {
    return typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
  }

  static boolean isInTheFormat(String typed) {
    final String SERIES_WITH_UP_TO_SIX_DIGITS = "(KK|KP|SK|SP|LK|IK|IP|JK|JP)\\d{1,6}";
    final String SERIES_WITH_NINE_DIGITS = "(TL|TW|NL|NW|UL|UP)\\d{9}";
    return printed(typed).matches(SERIES_WITH_UP_TO_SIX_DIGITS + "|" + SERIES_WITH_NINE_DIGITS);
  }
}
