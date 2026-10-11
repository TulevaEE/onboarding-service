package ee.tuleva.onboarding.accounting.directo;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

final class PersonalCodeChecksum {

  private static final Pattern CENTURY_DIGIT_AND_TEN_DIGITS = Pattern.compile("[1-8][0-9]{10}");

  private PersonalCodeChecksum() {}

  static void stopIfAnyPasses(Collection<String> codes) {
    long passing = codes.stream().distinct().filter(PersonalCodeChecksum::passes).count();
    if (passing > 0) {
      throw new IllegalStateException(
          "Directo dimension codes pass the personal code checksum: count=" + passing);
    }
  }

  static boolean passes(String code) {
    return CENTURY_DIGIT_AND_TEN_DIGITS.matcher(code).matches()
        && checkDigit(code) == Character.digit(code.charAt(10), 10);
  }

  private static int checkDigit(String code) {
    final int[] FIRST_WEIGHTS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 1};
    final int[] SECOND_WEIGHTS = {3, 4, 5, 6, 7, 8, 9, 1, 2, 3};
    int remainder = weightedRemainder(code, FIRST_WEIGHTS);
    if (remainder < 10) {
      return remainder;
    }
    remainder = weightedRemainder(code, SECOND_WEIGHTS);
    return remainder < 10 ? remainder : 0;
  }

  private static int weightedRemainder(String code, int[] weights) {
    return IntStream.range(0, weights.length)
            .map(index -> Character.digit(code.charAt(index), 10) * weights[index])
            .sum()
        % 11;
  }
}
