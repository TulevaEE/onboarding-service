package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeFlowTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 8, 27);

  @Test
  void aPurchaseIsValuedAtTodaysMark() {
    var flow =
        TradeFlow.between(
            List.of(mark("IE000QWCYQT0", "5.02")),
            List.of(security("IE000QWCYQT0", "64247801")),
            List.of(security("IE000QWCYQT0", "65053309")));

    assertThat(flow.atMark()).isEqualByComparingTo(new BigDecimal("4043650.16"));
    assertThat(flow.isComplete()).isTrue();
  }

  @Test
  void aDisposalIsNegative() {
    var flow =
        TradeFlow.between(
            List.of(mark("IE0009FT4LX4", "17.32")),
            List.of(security("IE0009FT4LX4", "18811874.1")),
            List.of(security("IE0009FT4LX4", "0")));

    assertThat(flow.atMark()).isEqualByComparingTo(new BigDecimal("-325821659.41"));
    assertThat(flow.isComplete()).isTrue();
  }

  @Test
  void aDayWithoutTradesIsZero() {
    var flow =
        TradeFlow.between(
            List.of(mark("IE000QWCYQT0", "5.02")),
            List.of(security("IE000QWCYQT0", "64247801")),
            List.of(security("IE000QWCYQT0", "64247801")));

    assertThat(flow.atMark()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(flow.isComplete()).isTrue();
  }

  @Test
  void aHoldingThatLeftTheReportIsMarkedAtThePriceThatReportCarried() {
    var flow =
        TradeFlow.between(
            List.of(mark("IE000QWCYQT0", "5.02")),
            List.of(priced("IE0009FT4LX4", "1000", "17.32"), security("IE000QWCYQT0", "1000")),
            List.of(security("IE000QWCYQT0", "1000")));

    assertThat(flow.atMark()).isEqualByComparingTo(new BigDecimal("-17320.00"));
    assertThat(flow.isComplete()).isTrue();
  }

  @Test
  void aHoldingWithNoMarkAnywhereIsNamedRatherThanSilentlyDropped() {
    var flow =
        TradeFlow.between(
            List.of(unmarked("IE0009FT4LX4"), mark("IE000QWCYQT0", "5.02")),
            List.of(security("IE0009FT4LX4", "1000"), security("IE000QWCYQT0", "1000")),
            List.of(security("IE0009FT4LX4", "2000"), security("IE000QWCYQT0", "2000")));

    assertThat(flow.atMark()).isEqualByComparingTo(new BigDecimal("5020.00"));
    assertThat(flow.unpricedIsins()).containsExactly("IE0009FT4LX4");
    assertThat(flow.isComplete()).isFalse();
  }

  @Test
  void anUnpricedHoldingNobodyTradedIsNotReportedAsMissing() {
    var flow =
        TradeFlow.between(
            List.of(unmarked("IE0009FT4LX4"), mark("IE000QWCYQT0", "5.02")),
            List.of(security("IE0009FT4LX4", "1000"), security("IE000QWCYQT0", "1000")),
            List.of(security("IE0009FT4LX4", "1000"), security("IE000QWCYQT0", "2000")));

    assertThat(flow.atMark()).isEqualByComparingTo(new BigDecimal("5020.00"));
    assertThat(flow.isComplete()).isTrue();
  }

  private SecurityData mark(String isin, String price) {
    return new SecurityData(
        isin,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new PriceSnapshot(new BigDecimal(price), NAV_DATE),
        new PriceSnapshot(new BigDecimal(price), NAV_DATE.minusDays(1)));
  }

  private SecurityData unmarked(String isin) {
    return new SecurityData(
        isin,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new PriceSnapshot(null, null),
        new PriceSnapshot(null, null));
  }

  private FundPosition security(String isin, String quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName(isin)
        .accountId(isin)
        .quantity(new BigDecimal(quantity))
        .build();
  }

  private FundPosition priced(String isin, String quantity, String marketPrice) {
    var position = security(isin, quantity);
    position.setMarketPrice(new BigDecimal(marketPrice));
    return position;
  }
}
