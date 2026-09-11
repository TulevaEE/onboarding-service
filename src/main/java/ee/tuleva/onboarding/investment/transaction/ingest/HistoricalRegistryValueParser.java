package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class HistoricalRegistryValueParser {

  private static final DateTimeFormatter SHEET_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final Pattern THOUSANDS_GROUPING_COMMA = Pattern.compile("^-?\\d{1,3}(,\\d{3})+$");
  private static final Pattern THOUSANDS_GROUPING_PERIOD =
      Pattern.compile("^-?\\d{1,3}(\\.\\d{3})+$");

  private HistoricalRegistryValueParser() {}

  static char decimalSeparatorFor(char delimiter) {
    return delimiter == ';' ? ',' : '.';
  }

  static @Nullable BigDecimal parseDecimal(
      @Nullable String raw, String column, char decimalSeparator) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.replace(" ", "").replace(" ", "");
    boolean hasComma = cleaned.contains(",");
    boolean hasPeriod = cleaned.contains(".");
    if (hasComma && hasPeriod) {
      cleaned =
          cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
              ? cleaned.replace(".", "").replace(',', '.')
              : cleaned.replace(",", "");
    } else if (hasComma || hasPeriod) {
      cleaned = resolveSingleSeparator(cleaned, hasComma ? ',' : '.', decimalSeparator);
    }
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new RowParseException("Invalid number: column=" + column + ", value=" + raw);
    }
  }

  private static String resolveSingleSeparator(
      String cleaned, char separator, char decimalSeparator) {
    Pattern groupingPattern =
        separator == ',' ? THOUSANDS_GROUPING_COMMA : THOUSANDS_GROUPING_PERIOD;
    if (!groupingPattern.matcher(cleaned).matches()) {
      return cleaned.replace(separator, '.');
    }
    boolean singleOccurrence = cleaned.indexOf(separator) == cleaned.lastIndexOf(separator);
    boolean isDecimalUsage = separator == decimalSeparator && singleOccurrence;
    return isDecimalUsage
        ? cleaned.replace(separator, '.')
        : cleaned.replace(String.valueOf(separator), "");
  }

  static @Nullable Instant parseInstant(@Nullable String raw, String column) {
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDateTime.parse(raw, SHEET_TIMESTAMP).toInstant(UTC);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(UTC);
    } catch (DateTimeParseException ignored) {
    }
    LocalDate date = parseDate(raw, column);
    return date == null ? null : date.atStartOfDay(UTC).toInstant();
  }

  static @Nullable LocalDate parseDate(@Nullable String raw, String column) {
    if (raw == null) {
      return null;
    }
    try {
      return LocalDate.parse(raw);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDate.parse(raw, ESTONIAN_DATE);
    } catch (DateTimeParseException e) {
      throw new RowParseException("Invalid date: column=" + column + ", value=" + raw);
    }
  }
}
