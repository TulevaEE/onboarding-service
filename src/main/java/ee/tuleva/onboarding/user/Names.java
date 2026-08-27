package ee.tuleva.onboarding.user;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Locale;

public class Names {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final String NAME_PART_SEPARATORS = " -'";
  private static final String SPLIT_KEEPING_SEPARATORS = "(?<=[ \\-'])|(?=[ \\-'])";

  public static String formatted(String name) {
    if (name == null || name.isBlank()) {
      return name;
    }
    return Arrays.stream(name.split(SPLIT_KEEPING_SEPARATORS))
        .map(Names::formattedPart)
        .collect(joining());
  }

  private static String formattedPart(String part) {
    if (part.isEmpty() || isSeparator(part) || hasDeliberateCasing(part)) {
      return part;
    }
    return capitalized(part);
  }

  private static boolean isSeparator(String part) {
    return part.length() == 1 && NAME_PART_SEPARATORS.contains(part);
  }

  private static boolean hasDeliberateCasing(String part) {
    boolean isAllLowerCase = part.equals(part.toLowerCase(ESTONIAN));
    boolean isAllUpperCase = part.equals(part.toUpperCase(ESTONIAN));
    return !isAllLowerCase && !isAllUpperCase;
  }

  private static String capitalized(String part) {
    String firstLetter = part.substring(0, 1);
    String rest = part.substring(1);
    return firstLetter.toUpperCase(ESTONIAN) + rest.toLowerCase(ESTONIAN);
  }
}
