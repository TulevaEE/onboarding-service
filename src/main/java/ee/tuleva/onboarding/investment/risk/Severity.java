package ee.tuleva.onboarding.investment.risk;

enum Severity {
  GREEN("✅"),
  YELLOW("⚠️"),
  RED("🔴");

  private final String icon;

  Severity(String icon) {
    this.icon = icon;
  }

  String icon() {
    return icon;
  }
}
