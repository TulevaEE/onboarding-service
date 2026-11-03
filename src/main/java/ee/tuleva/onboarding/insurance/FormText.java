package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class FormText {

  static final String FUND_NAME = TUV100.getDisplayName();
  static final String FUND_ISIN = TUV100.getIsin();

  private static final Locale ESTONIAN = Locale.of("et");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter DATE_DIGITS = DateTimeFormatter.ofPattern("ddMMyyyy");

  private FormText() {}

  static String date(LocalDate date) {
    return DATE.format(date);
  }

  static String dateDigits(LocalDate date) {
    return DATE_DIGITS.format(date);
  }

  static String country(@Nullable String countryCode) {
    return Locale.of("", requireNonNull(countryCode, "Country missing"))
        .getDisplayCountry(ESTONIAN);
  }

  static String countries(@Nullable List<String> countryCodes) {
    return requireNonNull(countryCodes, "Countries missing").stream()
        .map(FormText::country)
        .collect(joining(", "));
  }

  static String address(Address address) {
    return address.street()
        + ", "
        + address.postalCode()
        + " "
        + address.city()
        + ", "
        + country(address.countryCode());
  }

  static String fundWithIsin() {
    return FUND_NAME + ", ISIN " + FUND_ISIN;
  }
}
