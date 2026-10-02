package ee.tuleva.onboarding.investment.check.tracking;

enum GapCause {
  MISSING_PRICE("the missing price has to be inserted by hand, nothing backfills it"),
  MISSING_NAV(
      "that day's NAV has to be calculated and published; the next gap fill then checks it"),
  CHECK_FAILED("the check itself fails; it is retried every evening until the error is fixed");

  private final String whatFillsIt;

  GapCause(String whatFillsIt) {
    this.whatFillsIt = whatFillsIt;
  }

  String whatFillsIt() {
    return whatFillsIt;
  }
}
