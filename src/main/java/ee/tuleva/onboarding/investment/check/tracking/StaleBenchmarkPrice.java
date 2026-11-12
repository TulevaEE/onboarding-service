package ee.tuleva.onboarding.investment.check.tracking;

import java.time.LocalDate;

record StaleBenchmarkPrice(String benchmark, LocalDate wanted, LocalDate found) {

  String describe() {
    return "%s has no price for %s in any source, used %s".formatted(benchmark, wanted, found);
  }
}
