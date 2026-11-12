package ee.tuleva.onboarding.comparisons.fundvalue.validation;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

record InstrumentSource(
    String name, String displayName, String storageKey, int scale, BigDecimal roundingTolerance) {

  InstrumentSource(String name, String displayName, String storageKey, int scale) {
    this(name, displayName, storageKey, scale, ZERO);
  }
}
