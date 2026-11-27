package ee.tuleva.onboarding.investment.fees.instrument;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;

record InstrumentPrice(BigDecimal price, LocalDate priceDate, String source) {

  static final String NAV_REPORT = "NAV_REPORT";
  private static final int STORED_PRICE_SCALE = 8;

  InstrumentPrice {
    price = price.setScale(STORED_PRICE_SCALE, HALF_UP);
  }
}
