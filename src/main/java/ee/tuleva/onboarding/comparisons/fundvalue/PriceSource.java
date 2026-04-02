package ee.tuleva.onboarding.comparisons.fundvalue;

public enum PriceSource {
  BLACKROCK,
  MORNINGSTAR,
  EODHD,
  YAHOO;

  public static PriceSource fromProviderName(String providerName) {
    return switch (providerName) {
      case "BLACKROCK" -> BLACKROCK;
      case "MORNINGSTAR" -> MORNINGSTAR;
      case "EODHD" -> EODHD;
      case "YAHOO" -> YAHOO;
      default -> throw new IllegalArgumentException("Unknown provider: " + providerName);
    };
  }
}
