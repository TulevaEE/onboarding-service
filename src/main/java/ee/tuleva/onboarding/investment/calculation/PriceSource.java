package ee.tuleva.onboarding.investment.calculation;

public enum PriceSource {
  BLACKROCK,
  MORNINGSTAR,
  EODHD,
  YAHOO;

  static PriceSource fromProviderName(String providerName) {
    return switch (providerName) {
      case "BLACKROCK" -> BLACKROCK;
      case "MORNINGSTAR" -> MORNINGSTAR;
      case "EODHD" -> EODHD;
      case "YAHOO" -> YAHOO;
      default -> throw new IllegalArgumentException("Unknown provider: " + providerName);
    };
  }
}
