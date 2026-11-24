package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.InstrumentPrice.NAV_REPORT;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record NavHoldings(LocalDate navDate, List<NavAccountLine> securityLines) {

  BigDecimal unitsOf(String isin) {
    return linesOf(isin).stream().map(NavAccountLine::units).reduce(ZERO, BigDecimal::add);
  }

  Optional<InstrumentPrice> priceOf(String isin) {
    return linesOf(isin).stream()
        .map(NavAccountLine::marketPrice)
        .filter(Objects::nonNull)
        .filter(price -> price.signum() > 0)
        .findFirst()
        .map(price -> new InstrumentPrice(price, navDate, NAV_REPORT));
  }

  private List<NavAccountLine> linesOf(String isin) {
    return securityLines.stream().filter(line -> isin.equals(line.accountId())).toList();
  }
}
