package ee.tuleva.onboarding.investment.epis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class SummaryData {

  private SummaryData() {}

  static BigDecimal number(Map<String, Object> data, String key) {
    Object value = data.get(key);
    return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
  }

  static List<LocalDate> dates(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (!(value instanceof Collection<?> values)) {
      return List.of();
    }
    return values.stream().map(date -> LocalDate.parse(String.valueOf(date))).toList();
  }
}
