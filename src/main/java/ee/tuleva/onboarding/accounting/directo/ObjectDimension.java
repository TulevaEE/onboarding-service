package ee.tuleva.onboarding.accounting.directo;

import java.util.Arrays;

enum ObjectDimension {
  TEAM("10", "team"),
  FUND("20", "fund"),
  CLIENT("30", "client"),
  SUPPLIER("40", "supplier"),
  CAPITALIZATION("80", "capitalization");

  private final String level;
  private final String key;

  ObjectDimension(String level, String key) {
    this.level = level;
    this.key = key;
  }

  static ObjectDimension atLevel(String level) {
    return Arrays.stream(values())
        .filter(dimension -> dimension.level.equals(level))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Directo object level has no dimension: level=" + level));
  }

  String key() {
    return key;
  }
}
