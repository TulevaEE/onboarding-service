package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.EscalationRule.SISEKORD_4_P_11_8_CHECK;

import java.util.stream.Stream;

enum BreachSeverity {
  NOTICE("⚠️", "TD NOTICE — NO SISEKORD LIMIT"),
  BREACH("🛑", "TD BREACH DETECTED");

  private final String icon;
  private final String title;

  BreachSeverity(String icon, String title) {
    this.icon = icon;
    this.title = title;
  }

  static BreachSeverity of(TrackingDifferenceResult result) {
    return result.checkType() == SISEKORD_4_P_11_8_CHECK ? BREACH : NOTICE;
  }

  static BreachSeverity mostSevereOf(Stream<BreachSeverity> severities) {
    return severities.anyMatch(BREACH::equals) ? BREACH : NOTICE;
  }

  String icon() {
    return icon;
  }

  String headline() {
    return icon + " " + title;
  }
}
