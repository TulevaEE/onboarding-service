package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.UnitsBasis.SETTLED;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsBasis.TRADE_DATE;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT_SEB_REPORTS_INCOMPLETE;
import static ee.tuleva.onboarding.investment.fees.instrument.UnitsSource.NAV_REPORT_WITHOUT_SEB_REPORTS;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DayHoldingsTest {

  private static final String ISIN = "ZZ0000000001";
  private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 22);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);
  private static final InstrumentPrice FRIDAYS_NAV_PRICE =
      new InstrumentPrice(new BigDecimal("12.50"), FRIDAY, "NAV_REPORT");

  @Mock private FundNavQueryService navQueries;
  @Mock private FundPositionRepository positions;
  @Mock private OpenTradeSource openTradeSource;

  private DayHoldings holdings;

  @BeforeEach
  void onSunday() {
    holdings = new DayHoldings(SUNDAY, navQueries, positions, openTradeSource);
  }

  @Test
  void aDayWithoutANavTakesTheUnitsOfTheLastPublishedNav() {
    publishedNavOn(FRIDAY, "1000.000");

    assertThat(holdings.of(TUK75, ISIN, TRADE_DATE))
        .contains(new Holding(new BigDecimal("1000.000"), FRIDAY, NAV_REPORT, FRIDAYS_NAV_PRICE));
  }

  @Test
  void settledUnitsComeFromTheLastPositionReportAndItsPendingTrades() {
    publishedNavOn(FRIDAY, "1000.000");
    sebPositionOn(FRIDAY, "1000.000");
    given(openTradeSource.openTrades(FRIDAY))
        .willReturn(Optional.of(new OpenTrades(List.of(buy("120.000")), Set.of(), List.of())));

    assertThat(holdings.of(TUK75, ISIN, SETTLED))
        .hasValueSatisfying(
            holding -> {
              assertThat(holding.units()).isEqualByComparingTo("880.000");
              assertThat(holding.unitsDate()).isEqualTo(FRIDAY);
              assertThat(holding.source()).isEqualTo(UnitsSource.SETTLED);
              assertThat(holding.navPrice()).isEqualTo(FRIDAYS_NAV_PRICE);
            });
  }

  @Test
  void withoutThePendingReportTheSettledBasisTakesTheNavsUnitsAndSaysSo() {
    publishedNavOn(FRIDAY, "1000.000");
    given(positions.findLatestNavDateByFundAndAsOfDate(TUK75, SUNDAY))
        .willReturn(Optional.of(FRIDAY));
    given(openTradeSource.openTrades(FRIDAY)).willReturn(Optional.empty());

    assertThat(holdings.of(TUK75, ISIN, SETTLED))
        .contains(
            new Holding(
                new BigDecimal("1000.000"),
                FRIDAY,
                NAV_REPORT_WITHOUT_SEB_REPORTS,
                FRIDAYS_NAV_PRICE));
  }

  @Test
  void aPendingReportWithRowsNotReadForTheFundIsNotTrustedForItsSettledUnits() {
    publishedNavOn(FRIDAY, "1000.000");
    given(positions.findLatestNavDateByFundAndAsOfDate(TUK75, SUNDAY))
        .willReturn(Optional.of(FRIDAY));
    given(openTradeSource.openTrades(FRIDAY))
        .willReturn(Optional.of(new OpenTrades(List.of(), Set.of(TUK75), List.of())));

    assertThat(holdings.of(TUK75, ISIN, SETTLED))
        .hasValueSatisfying(
            holding -> assertThat(holding.source()).isEqualTo(NAV_REPORT_SEB_REPORTS_INCOMPLETE));
  }

  @Test
  void aPendingReportWithAClientNameNoFundAnswersToIsNotTrustedEither() {
    publishedNavOn(FRIDAY, "1000.000");
    given(positions.findLatestNavDateByFundAndAsOfDate(TUK75, SUNDAY))
        .willReturn(Optional.of(FRIDAY));
    given(openTradeSource.openTrades(FRIDAY))
        .willReturn(
            Optional.of(new OpenTrades(List.of(), Set.of(), List.of("Unrecognised client"))));

    assertThat(holdings.of(TUK75, ISIN, SETTLED))
        .hasValueSatisfying(
            holding -> assertThat(holding.source()).isEqualTo(NAV_REPORT_SEB_REPORTS_INCOMPLETE));
  }

  @Test
  void aNavOlderThanAWeekIsNoHolding() {
    given(navQueries.findLatestPublishedNavDateOnOrBefore(TUK75.getCode(), SUNDAY))
        .willReturn(Optional.of(SUNDAY.minusDays(8)));

    assertThat(holdings.of(TUK75, ISIN, TRADE_DATE)).isEmpty();
  }

  @Test
  void aPositionReportOlderThanAWeekLeavesTheSettledBasisOnTheNavsUnits() {
    publishedNavOn(FRIDAY, "1000.000");
    given(positions.findLatestNavDateByFundAndAsOfDate(TUK75, SUNDAY))
        .willReturn(Optional.of(SUNDAY.minusDays(8)));

    assertThat(holdings.of(TUK75, ISIN, SETTLED))
        .hasValueSatisfying(
            holding -> assertThat(holding.source()).isEqualTo(NAV_REPORT_WITHOUT_SEB_REPORTS));
  }

  @Test
  void eachFundsNavIsReadOnceForTheDay() {
    publishedNavOn(FRIDAY, "1000.000");

    holdings.of(TUK75, ISIN, TRADE_DATE);
    holdings.of(TUK75, "ZZ0000000002", TRADE_DATE);

    then(navQueries).should(times(1)).findPublishedCalculation(TUK75.getCode(), FRIDAY);
  }

  private void publishedNavOn(LocalDate navDate, String units) {
    given(navQueries.findLatestPublishedNavDateOnOrBefore(TUK75.getCode(), SUNDAY))
        .willReturn(Optional.of(navDate));
    given(navQueries.findPublishedCalculation(TUK75.getCode(), navDate))
        .willReturn(
            Optional.of(
                new NavCalculation(
                    UUID.randomUUID(),
                    Instant.parse("2026-11-21T10:00:00Z"),
                    List.of(
                        new NavAccountLine(
                            "SECURITY",
                            "Synthetic holding",
                            ISIN,
                            new BigDecimal(units),
                            new BigDecimal("12.50"),
                            new BigDecimal(units).multiply(new BigDecimal("12.50")))))));
  }

  private void sebPositionOn(LocalDate positionDate, String units) {
    given(positions.findLatestNavDateByFundAndAsOfDate(TUK75, SUNDAY))
        .willReturn(Optional.of(positionDate));
    given(positions.findByNavDateAndFundAndAccountType(positionDate, TUK75, SECURITY))
        .willReturn(
            List.of(
                FundPosition.builder()
                    .navDate(positionDate)
                    .fund(TUK75)
                    .accountType(SECURITY)
                    .accountName("Synthetic holding")
                    .accountId(ISIN)
                    .quantity(new BigDecimal(units))
                    .build()));
  }

  private static OpenTrade buy(String quantity) {
    return new OpenTrade(
        TUK75,
        BUY,
        new BigDecimal("1500.00"),
        null,
        ISIN,
        FRIDAY.plusDays(3),
        new BigDecimal(quantity),
        null);
  }
}
