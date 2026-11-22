package ee.tuleva.onboarding.investment.kid;

enum ScenarioKind {
  STRESS,
  UNFAVOURABLE,
  MODERATE,
  FAVOURABLE;

  String asTheKidNamesIt() {
    return switch (this) {
      case STRESS -> "Stress";
      case UNFAVOURABLE -> "Ebasoodne";
      case MODERATE -> "Mõõdukas";
      case FAVOURABLE -> "Soodne";
    };
  }
}
