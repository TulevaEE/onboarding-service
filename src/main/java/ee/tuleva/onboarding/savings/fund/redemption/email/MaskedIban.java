package ee.tuleva.onboarding.savings.fund.redemption.email;

import static java.util.stream.Collectors.joining;

import java.util.stream.IntStream;

final class MaskedIban {

  private static final int COUNTRY_CODE_AND_CHECK_DIGITS = 4;
  private static final int VISIBLE_TAIL = 4;
  private static final int PRINTED_GROUP_SIZE = 4;

  private MaskedIban() {}

  static String of(String iban) {
    int tailStart = iban.length() - VISIBLE_TAIL;
    return inPrintedGroups(
        iban.substring(0, COUNTRY_CODE_AND_CHECK_DIGITS)
            + "*".repeat(tailStart - COUNTRY_CODE_AND_CHECK_DIGITS)
            + iban.substring(tailStart));
  }

  private static String inPrintedGroups(String iban) {
    return IntStream.iterate(0, start -> start < iban.length(), start -> start + PRINTED_GROUP_SIZE)
        .mapToObj(
            start -> iban.substring(start, Math.min(start + PRINTED_GROUP_SIZE, iban.length())))
        .collect(joining(" "));
  }
}
