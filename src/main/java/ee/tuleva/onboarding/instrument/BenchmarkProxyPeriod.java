package ee.tuleva.onboarding.instrument;

import java.time.LocalDate;

record BenchmarkProxyPeriod(String holdingIsin, String proxyIsin, LocalDate validUntil) {

  boolean covers(LocalDate date) {
    return !date.isAfter(validUntil);
  }
}
