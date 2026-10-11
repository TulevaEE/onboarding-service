package ee.tuleva.onboarding.investment.check.tracking;

final class FailureReason {

  private FailureReason() {}

  static String of(Exception e) {
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }
}
