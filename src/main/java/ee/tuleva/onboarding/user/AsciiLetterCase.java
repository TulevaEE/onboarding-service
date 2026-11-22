package ee.tuleva.onboarding.user;

import org.jspecify.annotations.Nullable;

public final class AsciiLetterCase {

  private AsciiLetterCase() {}

  public static boolean equalIgnoringCase(@Nullable String one, @Nullable String other) {
    return one != null && other != null && fold(one).equals(fold(other));
  }

  public static String fold(String value) {
    return value
        .chars()
        .map(AsciiLetterCase::foldLetter)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }

  private static int foldLetter(int character) {
    return character >= 'A' && character <= 'Z' ? character + ('a' - 'A') : character;
  }
}
