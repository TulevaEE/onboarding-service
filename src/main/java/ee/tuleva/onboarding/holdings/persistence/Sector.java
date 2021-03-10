package ee.tuleva.onboarding.holdings.persistence;

import java.util.HashMap;
import java.util.Map;

public enum Sector {
  BASIC_MATERIALS(1),
  CONSUMER_CYCLICAL(2),
  FINANCIAL_SERVICES(3),
  REAL_ESTATE(4),
  CONSUMER_DEFENSIVE(5),
  HEALTHCARE(6),
  UTILITIES(7),
  COMMUNICATION_SERVICES(8),
  ENERGY(9),
  INDUSTRIALS(10),
  TECHNOLOGY(11);

  private int value;
  private static Map map = new HashMap<>();

  Sector(int value) {
    this.value = value;
  }

  static {
    for (Sector sector : Sector.values()) {
      map.put(sector.value, sector);
    }
  }

  public static Sector valueOf(int value) {
    return (Sector) map.get(value);
  }

  public int getValue() {
    return value;
  }
}
