package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT_SEB_REPORTS_INCOMPLETE;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT_WITHOUT_SEB_REPORTS;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.SETTLED;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
@RequiredArgsConstructor
class DayHoldings {

  static final int INPUTS_AT_MOST_DAYS_BEFORE_THE_DAY = 7;

  private final LocalDate day;
  private final FundNavQueryService navQueries;
  private final FundPositionRepository positions;
  private final OpenTradeSource openTradeSource;
  private final Map<TulevaFund, Optional<NavHoldings>> navHoldings =
      new EnumMap<>(TulevaFund.class);
  private final Map<TulevaFund, Optional<SebHoldings>> sebHoldings =
      new EnumMap<>(TulevaFund.class);
  private final Map<LocalDate, Optional<OpenTrades>> openTradesByPositionDate = new HashMap<>();

  Optional<Holding> of(TulevaFund fund, String isin, UnitsBasis basis) {
    var nav = navHoldingsOf(fund);
    var navPrice = nav.flatMap(holdings -> holdings.priceOf(isin)).orElse(null);
    return switch (basis) {
      case TRADE_DATE ->
          nav.map(
              holdings ->
                  new Holding(holdings.unitsOf(isin), holdings.navDate(), NAV_REPORT, navPrice));
      case SETTLED -> settled(fund, isin, nav, navPrice);
    };
  }

  private Optional<Holding> settled(
      TulevaFund fund, String isin, Optional<NavHoldings> nav, @Nullable InstrumentPrice navPrice) {
    var seb = sebHoldingsOf(fund);
    var fallbackSource =
        seb.isPresent() ? NAV_REPORT_SEB_REPORTS_INCOMPLETE : NAV_REPORT_WITHOUT_SEB_REPORTS;
    return seb.flatMap(
            holdings ->
                holdings
                    .settledUnitsOf(isin)
                    .map(units -> new Holding(units, holdings.positionDate(), SETTLED, navPrice)))
        .or(
            () ->
                nav.map(
                    holdings ->
                        new Holding(
                            holdings.unitsOf(isin), holdings.navDate(), fallbackSource, navPrice)));
  }

  private Optional<NavHoldings> navHoldingsOf(TulevaFund fund) {
    return navHoldings.computeIfAbsent(fund, this::readNavHoldings);
  }

  private Optional<NavHoldings> readNavHoldings(TulevaFund fund) {
    return navQueries
        .findLatestPublishedNavDateOnOrBefore(fund.getCode(), day)
        .filter(this::recentEnough)
        .flatMap(
            navDate ->
                navQueries
                    .findPublishedCalculation(fund.getCode(), navDate)
                    .map(calculation -> new NavHoldings(navDate, calculation.securityLines())));
  }

  private Optional<SebHoldings> sebHoldingsOf(TulevaFund fund) {
    return sebHoldings.computeIfAbsent(fund, this::readSebHoldings);
  }

  private Optional<SebHoldings> readSebHoldings(TulevaFund fund) {
    var holdings =
        positions
            .findLatestNavDateByFundAndAsOfDate(fund, day)
            .filter(this::recentEnough)
            .flatMap(
                positionDate ->
                    openTradesOn(positionDate)
                        .map(
                            openTrades ->
                                new SebHoldings(
                                    fund,
                                    positionDate,
                                    positionUnits(fund, positionDate),
                                    openTrades)));
    holdings
        .filter(seb -> !seb.everyRowIsRead())
        .ifPresent(
            seb ->
                log.warn(
                    "Pending report rows could not all be read, settled units fall back to the"
                        + " NAV's units: fund={}, positionDate={}, day={},"
                        + " unattributedClientNames={}",
                    fund,
                    seb.positionDate(),
                    day,
                    seb.openTrades().unattributedClientNames().size()));
    return holdings;
  }

  private Optional<OpenTrades> openTradesOn(LocalDate positionDate) {
    return openTradesByPositionDate.computeIfAbsent(positionDate, openTradeSource::openTrades);
  }

  private Map<String, BigDecimal> positionUnits(TulevaFund fund, LocalDate positionDate) {
    return positions.findByNavDateAndFundAndAccountType(positionDate, fund, SECURITY).stream()
        .flatMap(DayHoldings::unitsOf)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, BigDecimal::add));
  }

  private static Stream<Map.Entry<String, BigDecimal>> unitsOf(FundPosition position) {
    var isin = position.getAccountId();
    var quantity = position.getQuantity();
    return isin == null || quantity == null ? Stream.empty() : Stream.of(Map.entry(isin, quantity));
  }

  private boolean recentEnough(LocalDate inputDate) {
    return !inputDate.isBefore(day.minusDays(INPUTS_AT_MOST_DAYS_BEFORE_THE_DAY));
  }
}
