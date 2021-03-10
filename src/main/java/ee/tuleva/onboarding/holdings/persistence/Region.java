package ee.tuleva.onboarding.holdings.persistence;

import java.util.HashMap;
import java.util.Map;

public enum Region {
  UNITED_STATES(1),
  CANADA(2),
  LATIN_AMERICA(3),
  UNITED_KINGDOM(4),
  EUROZONE(5),
  EUROPE_EX_EURO(6),
  EUROPE_EMERGING(7),
  AFRICA(8),
  MIDDILE_EAST(9),
  JAPAN(10),
  AUSTRALASIA(11),
  ASIA_DEVELOPED(12),
  ASIA_EMERGING(13),
  EMERGING_MARKET(14),
  DEVELOPED_COUNTRY(15),
  NOT_CLASSIFIED(16);

  private int value;
  private static Map map = new HashMap<>();

  Region(int value) {
    this.value = value;
  }

  static {
    for (Region region : Region.values()) {
      map.put(region.value, region);
    }
  }

  public static Region valueOf(int value) {
    return (Region) map.get(value);
  }

  public int getValue() {
    return value;
  }
}
