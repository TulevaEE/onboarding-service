package ee.tuleva.onboarding.investment.epis.parser;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class EpisNumbers {

  private static final Pattern COMMA_GROUPED = Pattern.compile("^-?\\d{1,3}(,\\d{3})+$");
  private static final Pattern PERIOD_GROUPED = Pattern.compile("^-?\\d{1,3}(\\.\\d{3})+$");

  private EpisNumbers() {}

  public static @Nullable BigDecimal parseNumber(
      @Nullable String value, DecimalConvention convention) {
    if (value == null) {
      return null;
    }
    String cleaned = value.replace("%", "").replaceAll("[\\s\\u00A0]", "");
    if (cleaned.isEmpty()) {
      return null;
    }
    boolean hasComma = cleaned.indexOf(',') >= 0;
    boolean hasPeriod = cleaned.indexOf('.') >= 0;
    if (hasComma && hasPeriod) {
      cleaned =
          cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
              ? cleaned.replace(".", "").replace(',', '.')
              : cleaned.replace(",", "");
    } else if (hasComma || hasPeriod) {
      cleaned = resolveSingleSeparator(cleaned, hasComma ? ',' : '.', convention);
    }
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Unparseable EPIS number: value=" + value + ", convention=" + convention, e);
    }
  }

  private static String resolveSingleSeparator(
      String cleaned, char separator, DecimalConvention convention) {
    Pattern grouping = separator == ',' ? COMMA_GROUPED : PERIOD_GROUPED;
    boolean isGrouping = grouping.matcher(cleaned).matches();
    boolean isConventionDecimal = separator == convention.decimalSeparator();
    boolean singleOccurrence = cleaned.indexOf(separator) == cleaned.lastIndexOf(separator);
    boolean isDecimalUsage = isConventionDecimal && singleOccurrence;
    if (isGrouping && !isDecimalUsage) {
      return cleaned.replace(String.valueOf(separator), "");
    }
    return cleaned.replace(separator, '.');
  }
}
