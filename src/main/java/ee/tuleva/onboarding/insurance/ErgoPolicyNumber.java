package ee.tuleva.onboarding.insurance;

import java.util.Optional;

record ErgoPolicyNumber(String series, String number) {

  static Optional<ErgoPolicyNumber> parse(String policyNumber) {
    final String PRINTED_PREFIX = "705";
    String digits = policyNumber.replaceAll("\\D", "");
    String afterPrefix =
        digits.length() == 11 && digits.startsWith(PRINTED_PREFIX)
            ? digits.substring(PRINTED_PREFIX.length())
            : digits;
    return afterPrefix.length() == 8
        ? Optional.of(new ErgoPolicyNumber(afterPrefix.substring(0, 2), afterPrefix.substring(2)))
        : Optional.empty();
  }
}
