package ee.tuleva.onboarding.swedbank.statement;

import java.util.List;
import java.util.function.Function;

class Require {

  static <T> T exactlyOne(List<T> list, Function<List<T>, String> exceptionMessageGenerator) {
    if (list == null || list.size() != 1) {
      throw new BankStatementParseException(
          exceptionMessageGenerator.apply(list == null ? List.of() : list));
    }
    return list.getFirst();
  }

  static <T> T exactlyOne(List<T> list, String entityName) {
    return exactlyOne(
        list, (lst) -> "Expected exactly one " + entityName + ", but found: " + lst.size());
  }

  static <T> T atMostOne(List<T> list, Function<List<T>, String> exceptionMessageGenerator) {
    if (list != null && list.size() > 1) {
      throw new BankStatementParseException(exceptionMessageGenerator.apply(list));
    }
    return (list == null || list.isEmpty()) ? null : list.getFirst();
  }

  static <T> T atMostOne(List<T> list, String entityName) {
    return atMostOne(
        list, (lst) -> "Expected at most one " + entityName + ", but found: " + lst.size());
  }

  static <T> T notNull(T value, String entityName) {
    if (value == null) {
      throw new BankStatementParseException(entityName + " is required");
    }
    return value;
  }

  static String notNullOrBlank(String value, String entityName) {
    if (value == null || value.isBlank()) {
      throw new BankStatementParseException(entityName + " is required");
    }
    return value;
  }

  private Require() {
    // Utility class
  }
}
