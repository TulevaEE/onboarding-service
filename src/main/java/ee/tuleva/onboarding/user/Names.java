package ee.tuleva.onboarding.user;

import java.util.Locale;

public class Names {

  private static final Locale ESTONIAN = Locale.of("et");

  public static String formatted(String name) {
    if (name == null || name.isBlank()) {
      return name;
    }
    StringBuilder result = new StringBuilder(name.length());
    StringBuilder token = new StringBuilder();
    for (char c : name.toCharArray()) {
      if (c == ' ' || c == '-' || c == '\'') {
        result.append(formattedToken(token.toString())).append(c);
        token.setLength(0);
      } else {
        token.append(c);
      }
    }
    return result.append(formattedToken(token.toString())).toString();
  }

  private static String formattedToken(String token) {
    if (token.isEmpty()) {
      return token;
    }
    boolean allLower = token.equals(token.toLowerCase(ESTONIAN));
    boolean allUpper = token.equals(token.toUpperCase(ESTONIAN));
    if (!allLower && !allUpper) {
      return token;
    }
    return token.substring(0, 1).toUpperCase(ESTONIAN) + token.substring(1).toLowerCase(ESTONIAN);
  }
}
