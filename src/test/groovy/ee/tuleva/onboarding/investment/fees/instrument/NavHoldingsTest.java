package ee.tuleva.onboarding.investment.fees.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NavHoldingsTest {

  private static final String ISIN = "ZZ0000000001";
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 20);

  @Test
  void theUnitsAreTheInstrumentsSecurityLines() {
    var holdings =
        new NavHoldings(
            NAV_DATE,
            List.of(line(ISIN, "1000.000", "12.50"), line("ZZ0000000002", "5.000", "10.00")));

    assertThat(holdings.unitsOf(ISIN)).isEqualByComparingTo("1000.000");
  }

  @Test
  void anInstrumentTheNavDoesNotHoldHasNoUnitsAndNoPrice() {
    var holdings = new NavHoldings(NAV_DATE, List.of(line("ZZ0000000002", "5.000", "10.00")));

    assertThat(holdings.unitsOf(ISIN)).isEqualByComparingTo("0");
    assertThat(holdings.priceOf(ISIN)).isEmpty();
  }

  @Test
  void thePriceInTheNavReportIsDatedTheNavDate() {
    var holdings = new NavHoldings(NAV_DATE, List.of(line(ISIN, "1000.000", "12.50")));

    assertThat(holdings.priceOf(ISIN))
        .contains(new InstrumentPrice(new BigDecimal("12.50"), NAV_DATE, "NAV_REPORT"));
  }

  @Test
  void aLineWithoutAPriceGivesNoPrice() {
    var holdings = new NavHoldings(NAV_DATE, List.of(line(ISIN, "1000.000", null)));

    assertThat(holdings.priceOf(ISIN)).isEmpty();
  }

  private static NavAccountLine line(String isin, String quantity, @Nullable String price) {
    return new NavAccountLine(
        "SECURITY",
        "Synthetic holding",
        isin,
        new BigDecimal(quantity),
        price == null ? null : new BigDecimal(price),
        null);
  }
}
