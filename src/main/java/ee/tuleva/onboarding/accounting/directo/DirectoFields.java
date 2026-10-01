package ee.tuleva.onboarding.accounting.directo;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class DirectoFields {

  private DirectoFields() {}

  static <T> T require(@Nullable T value, String field) {
    if (value == null || value instanceof String text && text.isBlank()) {
      throw new IllegalArgumentException("Directo field missing: field=" + field);
    }
    return value;
  }

  static LocalDate date(String value, String field) {
    final int ISO_DATE_LENGTH = 10;
    try {
      return LocalDate.parse(
          value.length() > ISO_DATE_LENGTH ? value.substring(0, ISO_DATE_LENGTH) : value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Directo date unparseable: field=" + field);
    }
  }

  static List<String> codes(@Nullable String commaSeparated) {
    if (commaSeparated == null) {
      return List.of();
    }
    return Arrays.stream(commaSeparated.split(","))
        .map(String::trim)
        .filter(code -> !code.isEmpty())
        .toList();
  }
}
