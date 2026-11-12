package ee.tuleva.onboarding.investment.kid.performance;

import ee.tuleva.onboarding.comparisons.fundvalue.EquityCappedSeriesRefusedException;

class PastPerformanceRefusedException extends RuntimeException {

  PastPerformanceRefusedException(String message) {
    super(message);
  }

  PastPerformanceRefusedException(EquityCappedSeriesRefusedException cause) {
    super(cause.getMessage(), cause);
  }
}
