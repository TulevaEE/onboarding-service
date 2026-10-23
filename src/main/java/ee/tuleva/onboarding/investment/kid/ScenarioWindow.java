package ee.tuleva.onboarding.investment.kid;

import java.time.LocalDate;

record ScenarioWindow(
    WindowSource source, LocalDate start, LocalDate end, int months, double grossFactor) {

  double netFactor(double ongoingCharges) {
    return grossFactor * Math.pow(1 - ongoingCharges, months / 12.0);
  }
}
