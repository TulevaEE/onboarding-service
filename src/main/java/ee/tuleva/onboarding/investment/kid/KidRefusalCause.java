package ee.tuleva.onboarding.investment.kid;

import static java.util.Arrays.stream;

import java.util.Optional;

enum KidRefusalCause {
  NO_OCF_IN_FORCE("No OCF in force at the month end"),
  SERIES_STOPPED("KID series stopped"),
  NO_PRICES("No prices for the KID series");

  private static final String TEXT_ENDS = ": ";

  private final String text;

  KidRefusalCause(String text) {
    this.text = text;
  }

  String reason(String details) {
    return text + TEXT_ENDS + details;
  }

  static Optional<KidRefusalCause> of(String reason) {
    return stream(values()).filter(cause -> reason.startsWith(cause.text + TEXT_ENDS)).findFirst();
  }
}
