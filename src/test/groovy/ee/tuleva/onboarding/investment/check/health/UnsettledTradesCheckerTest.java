package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.transaction.OpenTrade;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnsettledTradesCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);
  private static final LocalDate SETTLEMENT = LocalDate.of(2026, 10, 22);
  private static final Instant TRADE_TIME = Instant.parse("2026-10-16T09:37:55Z");

  private final UnsettledTradesChecker checker = new UnsettledTradesChecker();

  private final OpenTrade developedWorldPurchase =
      trade(BUY, "115536.00", "DLA1287775", "IE00BFG1TM61", SETTLEMENT);
  private final OpenTrade emergingMarketsPurchase =
      trade(BUY, "1107572.00", "DLA1287774", "IE00BKPTWY98", SETTLEMENT);
  private final OpenTrade amundiPurchaseSettledToday =
      trade(BUY, "3088796.84", "DLA1289466", "IE000QWCYQT0", NAV_DATE);
  private final OpenTrade xtrackersPurchaseSettledToday =
      trade(BUY, "2730597.59", "DLA1289465", "IE000I9HGDZ3", NAV_DATE);

  @Test
  void failsWhenSebStillCountsAPurchaseThatSettledAndLeftTheTradeListAsOwed() {
    var today = trades(developedWorldPurchase, emergingMarketsPurchase);
    var previous = trades(amundiPurchaseSettledToday, xtrackersPurchaseSettledToday);

    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(payables("-4311904.84")), List.of(), today, previous);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                FAIL,
                "Trade payables 4311904.84, open buys 1223108.00, excess 3088796.84, NAV too low."
                    + " Cause: DLA1289466 IE000QWCYQT0 3088796.84 left SEB's trade list,"
                    + " settlement 2026-10-20"));
  }

  @Test
  void listsTheTradesThatLeftTheTradeListWhenNoneEqualsTheExcess() {
    var today = trades(developedWorldPurchase, emergingMarketsPurchase);
    var previous = trades(amundiPurchaseSettledToday, xtrackersPurchaseSettledToday);

    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(payables("-1223208.00")), List.of(), today, previous);

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .containsExactly(
            "Trade payables 1223208.00, open buys 1223108.00, excess 100.00, NAV too low."
                + " Left SEB's trade list: DLA1289465 IE000I9HGDZ3 2730597.59, settlement"
                + " 2026-10-20; DLA1289466 IE000QWCYQT0 3088796.84, settlement 2026-10-20");
  }

  @Test
  void saysNoTradeLeftTheTradeListWhenTheExcessHasNoSuchCause() {
    var today = trades(developedWorldPurchase, emergingMarketsPurchase);

    var findings =
        checker.check(TUK75, NAV_DATE, List.of(payables("-1223208.00")), List.of(), today, today);

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .containsExactly(
            "Trade payables 1223208.00, open buys 1223108.00, excess 100.00, NAV too low."
                + " No trade left SEB's trade list");
  }

  @Test
  void namesOnlyTradesOfTheSideThatIsOff() {
    var today = trades(developedWorldPurchase, emergingMarketsPurchase);
    var previous =
        trades(
            developedWorldPurchase,
            emergingMarketsPurchase,
            trade(SELL, "100.00", "DLA1289470", "IE00BFG1TM61", NAV_DATE));

    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(payables("-1223208.00")), List.of(), today, previous);

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .containsExactly(
            "Trade payables 1223208.00, open buys 1223108.00, excess 100.00, NAV too low."
                + " No trade left SEB's trade list");
  }

  @Test
  void leavesTheSettlementDateOutForATradeThatHadNone() {
    var today = trades(developedWorldPurchase, emergingMarketsPurchase);
    var previous =
        trades(
            developedWorldPurchase,
            emergingMarketsPurchase,
            trade(BUY, "100.00", "DLA1289471", "IE00BKPTWY98", null));

    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(payables("-1223208.00")), List.of(), today, previous);

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .containsExactly(
            "Trade payables 1223208.00, open buys 1223108.00, excess 100.00, NAV too low."
                + " Cause: DLA1289471 IE00BKPTWY98 100.00 left SEB's trade list");
  }

  @Test
  void doesNotNameATradeWhoseReferenceSebAddedOnlyOnTheNextList() {
    var yesterdaysRowWithoutReference =
        new OpenTrade(
            TUK75,
            BUY,
            new BigDecimal("115536.00"),
            null,
            "IE00BFG1TM61",
            SETTLEMENT,
            new BigDecimal("100"),
            TRADE_TIME);
    var today = trades(developedWorldPurchase, emergingMarketsPurchase);
    var previous = trades(yesterdaysRowWithoutReference);

    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(payables("-1223208.00")), List.of(), today, previous);

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .containsExactly(
            "Trade payables 1223208.00, open buys 1223108.00, excess 100.00, NAV too low."
                + " No trade left SEB's trade list");
  }

  @Test
  void passesOnceThePayablesEqualThePurchasesStillOpen() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-1223108.00")),
            List.of(),
            trades(developedWorldPurchase, emergingMarketsPurchase),
            trades(amundiPurchaseSettledToday));

    assertThat(findings).isEmpty();
  }

  @Test
  void passesWhenTheTradeListHasAPurchaseSebHasNotBookedYet() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-115536.00")),
            List.of(),
            trades(developedWorldPurchase, emergingMarketsPurchase),
            null);

    assertThat(findings).isEmpty();
  }

  @Test
  void passesWithinACentOfRounding() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-1223108.01")),
            List.of(),
            trades(developedWorldPurchase, emergingMarketsPurchase),
            null);

    assertThat(findings).isEmpty();
  }

  @Test
  void failsWhenSebStillCountsASaleThatLeftTheTradeListAsReceivable() {
    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(), List.of(receivables("3318180.78")), trades(), null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                FAIL,
                "Trade receivables 3318180.78, open sells 0.00, excess 3318180.78, NAV too high."
                    + " Previous SEB trade list missing"));
  }

  @Test
  void ignoresAnotherFundsOpenTrades() {
    var otherFundsPurchase =
        new OpenTrade(
            TUV100,
            BUY,
            new BigDecimal("4311904.84"),
            "DLA1",
            "IE00BFG1TM61",
            SETTLEMENT,
            new BigDecimal("100"),
            TRADE_TIME);

    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-4311904.84")),
            List.of(),
            new OpenTrades(List.of(otherFundsPurchase), Set.of(), List.of()),
            null);

    assertThat(findings).extracting(HealthCheckFinding::severity).containsExactly(FAIL);
  }

  @Test
  void cannotRunWhenThePositionsOweForTradesButTheTradeListIsNotImported() {
    var findings =
        checker.check(TUK75, NAV_DATE, List.of(payables("-1223108.00")), List.of(), null, null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                NOT_RUN,
                "Not checked: no SEB trade list for 2026-10-20; trade payables 1223108.00,"
                    + " receivables 0.00"));
  }

  @Test
  void cannotRunWhenOneOfTheFundsOwnTradeRowsIsIncomplete() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-1223108.00")),
            List.of(),
            new OpenTrades(List.of(developedWorldPurchase), Set.of(TUK75), List.of()),
            null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                NOT_RUN,
                "Not checked: SEB's trade list has a row of this fund without a side or amount"));
  }

  @Test
  void stillChecksAFundWhenOnlyAnotherFundsTradeRowIsIncomplete() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-4311904.84")),
            List.of(),
            new OpenTrades(
                List.of(developedWorldPurchase, emergingMarketsPurchase),
                Set.of(TUV100),
                List.of()),
            null);

    assertThat(findings).extracting(HealthCheckFinding::severity).containsExactly(FAIL);
  }

  @Test
  void stillPassesAFundWhoseTradesBalanceWhenAnotherRowNamesNoKnownFund() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-1223108.00")),
            List.of(),
            new OpenTrades(
                List.of(developedWorldPurchase, emergingMarketsPurchase),
                Set.of(),
                List.of("Tuleva Vabatahtlik Pensionifond")),
            null);

    assertThat(findings).isEmpty();
  }

  @Test
  void cannotTellAnExcessFromATradeWhoseFundNameIsNotRecognised() {
    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payables("-4311904.84")),
            List.of(),
            new OpenTrades(
                List.of(developedWorldPurchase, emergingMarketsPurchase),
                Set.of(),
                List.of("Tuleva Vabatahtlik Pensionifond", "(no client name)")),
            null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                NOT_RUN,
                "Not checked: excess 3088796.84, and SEB's trade list has rows of no known fund:"
                    + " [Tuleva Vabatahtlik Pensionifond, (no client name)]"));
  }

  @Test
  void hasNothingToCheckWhenThePositionsNeitherOweNorAreOwedForTrades() {
    var findings =
        checker.check(
            TUK75, NAV_DATE, List.of(payables("0.00")), List.of(receivables("0.00")), null, null);

    assertThat(findings).isEmpty();
  }

  @Test
  void cannotRunWhenATradePayablesOrReceivablesRowHasNoAmount() {
    var payablesWithoutAmount =
        FundPosition.builder()
            .navDate(NAV_DATE)
            .fund(TUK75)
            .accountType(LIABILITY)
            .accountName("Total payables of unsettled transactions")
            .build();

    var findings =
        checker.check(
            TUK75,
            NAV_DATE,
            List.of(payablesWithoutAmount),
            List.of(),
            trades(developedWorldPurchase),
            null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                NOT_RUN,
                "Not checked: a trade payables or receivables row has no amount"));
  }

  private OpenTrades trades(OpenTrade... trades) {
    return new OpenTrades(List.of(trades), Set.of(), List.of());
  }

  private static OpenTrade trade(
      TransactionType side, String total, String reference, String isin, LocalDate settlement) {
    return new OpenTrade(
        TUK75,
        side,
        new BigDecimal(total),
        reference,
        isin,
        settlement,
        new BigDecimal("100"),
        TRADE_TIME);
  }

  private FundPosition payables(String marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(LIABILITY)
        .accountName("Total payables of unsettled transactions")
        .marketValue(new BigDecimal(marketValue))
        .build();
  }

  private FundPosition receivables(String marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(RECEIVABLES)
        .accountName("Total receivables of unsettled transactions")
        .marketValue(new BigDecimal(marketValue))
        .build();
  }
}
