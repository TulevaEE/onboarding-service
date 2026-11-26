package ee.tuleva.onboarding.savings.fund.pricecheck;

enum DealingPriceVerdict {
  PRICED_AT_THE_NAV,
  ROUNDING_ONLY,
  PRICE_DEVIATES,
  NAV_DIFFERS_FROM_PUBLISHED,
  NAV_NOT_PUBLISHED,
  INCOMPLETE_BOOKING;

  boolean isReported() {
    return this != PRICED_AT_THE_NAV && this != ROUNDING_ONLY;
  }
}
