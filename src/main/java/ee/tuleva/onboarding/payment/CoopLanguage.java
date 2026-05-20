package ee.tuleva.onboarding.payment;

public final class CoopLanguage {

  private CoopLanguage() {}

  // Coop uses country code "ee" for Estonian, not ISO 639 "et".
  public static String code(String currentLanguage) {
    return switch (currentLanguage) {
      case "en" -> "en";
      case "ru" -> "ru";
      default -> "ee";
    };
  }
}
