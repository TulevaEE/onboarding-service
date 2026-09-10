package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreachMessageFormatterTest {

  private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 9, 1);

  @Test
  void namesTheUnbookedRedemptionPayoutOnAnExecutionDate() {
    var message =
        new BreachMessageFormatter(
                tuk75On20260901(),
                false,
                new RedemptionCycleHint(true, new BigDecimal("5928109.00"), BigDecimal.ZERO))
            .format();

    assertThat(message)
        .contains("NAV bridge (EUR)")
        .contains("UNEXPLAINED")
        .contains("5,888,679.04")
        .contains("No security quantity changed")
        .contains("PEVA/RAVA execution date")
        .contains("R21 RAVA payout")
        .contains("that is the unexplained amount")
        .contains("same price both days, contributes nothing to the residual");
  }

  @Test
  void fallsBackToTheDateAloneWhenNoCycleFiguresAreIngested() {
    var message =
        new BreachMessageFormatter(
                tuk75On20260901(), false, RedemptionCycleHint.executionDateWithoutFigures())
            .format();

    assertThat(message)
        .contains("PEVA/RAVA execution date")
        .contains("No R17/R21 figures are ingested")
        .doesNotContain("R21 RAVA payout");
  }

  @Test
  void theActionLinePointsAwayFromPricesWhenNothingTraded() {
    var message =
        new BreachMessageFormatter(
                tuk75On20260901(), false, RedemptionCycleHint.executionDateWithoutFigures())
            .format();

    assertThat(message)
        .contains(
            "Action: 5,888,679.04 EUR unexplained and no quantity moved — this is not prices and"
                + " not trading. Check cash, units outstanding and the liability lines (payables,"
                + " pending redemptions).")
        .doesNotContain("Action: check NAV calculation — weights, prices, cash, fees");
  }

  @Test
  void theActionLinePointsAtSettlementWhenQuantitiesMoved() {
    var traded =
        tuk75On20260901().toBuilder().navFlow(navFlow(new BigDecimal("5888679.04"), true)).build();

    var message =
        new BreachMessageFormatter(traded, false, RedemptionCycleHint.ordinaryDay()).format();

    assertThat(message)
        .contains("Quantities moved today — check trade settlement (cash paid vs marked value)");
  }

  @Test
  void theActionLineSaysTheNavReconcilesWhenOnlyTheModelTdBreached() {
    var explained = tuk75On20260901().toBuilder().navResidualBreach(false).build();

    var message =
        new BreachMessageFormatter(explained, false, RedemptionCycleHint.ordinaryDay()).format();

    assertThat(message)
        .contains("the NAV itself reconciles")
        .contains("the gap is model-vs-fund weighting, not the NAV calculation");
  }

  @Test
  void theGenericActionLineRemainsWhenNoBridgeCouldBeBuilt() {
    var noBridge = tuk75On20260901().toBuilder().navFlow(null).build();

    var message =
        new BreachMessageFormatter(noBridge, false, RedemptionCycleHint.ordinaryDay()).format();

    assertThat(message)
        .contains("Action: check NAV calculation — weights, prices, cash, fees")
        .doesNotContain("NAV bridge (EUR)");
  }

  @Test
  void saysNothingAboutCyclesOnAnOrdinaryDay() {
    var message =
        new BreachMessageFormatter(tuk75On20260901(), false, RedemptionCycleHint.ordinaryDay())
            .format();

    assertThat(message).doesNotContain("PEVA/RAVA").contains("NAV bridge (EUR)");
  }

  @Test
  void aBenchmarkCheckCarriesNeitherActionLineNorBridge() {
    var benchmark = tuk75On20260901().toBuilder().checkType(BENCHMARK).build();

    var message =
        new BreachMessageFormatter(benchmark, false, RedemptionCycleHint.ordinaryDay()).format();

    assertThat(message)
        .doesNotContain("Action:")
        .doesNotContain("NAV bridge (EUR)")
        .doesNotContain("NAV residual");
  }

  @Test
  void saysSoWhenNoHoldingMovedInPriceEither() {
    var frozen =
        tuk75On20260901().toBuilder()
            .navFlow(navFlow(new BigDecimal("5888679.04"), false, BigDecimal.ZERO))
            .build();

    var message =
        new BreachMessageFormatter(frozen, false, RedemptionCycleHint.ordinaryDay()).format();

    assertThat(message)
        .contains("No security quantity changed")
        .contains("No holding moved in price either.");
  }

  @Test
  void namesTheR17PikAlongsideTheRavaPayout() {
    var message =
        new BreachMessageFormatter(
                tuk75On20260901(),
                false,
                new RedemptionCycleHint(
                    true, new BigDecimal("5928109.00"), new BigDecimal("412000.00")))
            .format();

    assertThat(message).contains("R21 RAVA payout").contains("R17 PIK").contains("412,000.00");
  }

  @Test
  void cycleFiguresStillGetNamedWhenNoBridgeCouldBeBuilt() {
    var noBridge = tuk75On20260901().toBuilder().navFlow(null).build();

    var message =
        new BreachMessageFormatter(
                noBridge, false, new RedemptionCycleHint(true, new BigDecimal("5928109.00"), null))
            .format();

    assertThat(message)
        .contains("R21 RAVA payout")
        .doesNotContain("that is the unexplained amount")
        .doesNotContain("look wider than the redemption leg");
  }

  @Test
  void aPayoutThatDoesNotMatchSendsTheSearchWider() {
    var message =
        new BreachMessageFormatter(
                tuk75On20260901(),
                false,
                new RedemptionCycleHint(true, new BigDecimal("120000.00"), null))
            .format();

    assertThat(message)
        .contains("R21 RAVA payout")
        .contains("does not account for the unexplained amount")
        .doesNotContain("that is the unexplained amount");
  }

  @Test
  void aCycleThatMovedNoMoneyCannotExplainAnything() {
    var message =
        new BreachMessageFormatter(
                tuk75On20260901(), false, new RedemptionCycleHint(true, BigDecimal.ZERO, null))
            .format();

    assertThat(message)
        .contains("does not account for the unexplained amount")
        .doesNotContain("that is the unexplained amount");
  }

  // A price stale on both legs returns 0.00% on the instrument and 0.00% on its index, so the row
  // cancels out of the tracking difference exactly. Listing it as a plain attribution row is what
  // sent the 01.09 diagnosis chasing two BlackRock lines the NAV report had flagged with a stale
  // price date, when neither could be the cause.
  @Test
  void aHoldingStaleOnBothLegsIsNamedAsCancellingOutRatherThanListedPlainly() {
    var benchmarkModel =
        tuk75On20260901().toBuilder()
            .checkType(BENCHMARK_MODEL)
            .securityAttributions(
                List.of(
                    benchmarkAttribution("IE00BFG1TM61", "0", "0"),
                    benchmarkAttribution("IE000I9HGDZ3", "0.0009", "-0.0022")))
            .build();

    var message =
        new BreachMessageFormatter(benchmarkModel, false, RedemptionCycleHint.ordinaryDay())
            .format();

    assertThat(message)
        .contains("IE00BFG1TM61: instrument 0.00%, index 0.00%")
        .contains("stale on both legs, cancels out — not the cause");
  }

  // An instrument that did not move while its index did is a real divergence, not a stale price,
  // so it must keep its plain row.
  @Test
  void aHoldingFlatAgainstAMovingIndexIsNotDismissedAsStale() {
    var benchmarkModel =
        tuk75On20260901().toBuilder()
            .checkType(BENCHMARK_MODEL)
            .securityAttributions(List.of(benchmarkAttribution("IE00BFG1TM61", "0", "-0.0022")))
            .build();

    var message =
        new BreachMessageFormatter(benchmarkModel, false, RedemptionCycleHint.ordinaryDay())
            .format();

    assertThat(message).doesNotContain("cancels out");
  }

  private SecurityAttribution benchmarkAttribution(
      String isin, String securityReturn, String benchmarkReturn) {
    return new SecurityAttribution(
        isin,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        new BigDecimal(securityReturn),
        new BigDecimal(benchmarkReturn),
        BigDecimal.ZERO);
  }

  // On 01.09 no security quantity changed at all, and that was one of the three facts that made
  // the diagnosis. The boolean says whether quantities moved; the EUR figure says what they were
  // worth, which is what separates a switch day from a settlement miss.
  @Test
  void theBridgeNamesWhatTheTradesWereWorthWhenQuantitiesMoved() {
    var traded =
        tuk75On20260901().toBuilder()
            .navFlow(
                navFlow(
                    new BigDecimal("5888679.04"),
                    true,
                    new BigDecimal("-1735979.79"),
                    new BigDecimal("-4045261.18")))
            .build();

    var message =
        new BreachMessageFormatter(traded, false, RedemptionCycleHint.ordinaryDay()).format();

    assertThat(message).contains("Trades moved").contains("-4,045,261.18");
  }

  private TrackingDifferenceResult tuk75On20260901() {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(EXECUTION_DATE)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(new BigDecimal("0.005239"))
        .fundReturn(new BigDecimal("0.003680"))
        .benchmarkReturn(new BigDecimal("-0.001559"))
        .breach(true)
        .navResidual(new BigDecimal("0.005226"))
        .navResidualBreach(true)
        .bodImpliedFundReturn(new BigDecimal("-0.001546"))
        .cashDrag(BigDecimal.ZERO)
        .feeDrag(new BigDecimal("-0.000006"))
        .residual(new BigDecimal("0.005226"))
        .securityAttributions(
            List.of(
                attribution("IE000QWCYQT0", "-0.0068", "-0.0031"),
                attribution("IE000I9HGDZ3", "0.0009", "-0.0022"),
                attribution("IE00BFG1TM61", "0.0018", "0"),
                attribution("IE00BKPTWY98", "0.0040", "0")))
        .navFlow(navFlow(new BigDecimal("5888679.04"), false))
        .build();
  }

  private NavFlowReconciliation navFlow(BigDecimal unexplained, boolean securityQuantitiesChanged) {
    return navFlow(unexplained, securityQuantitiesChanged, new BigDecimal("-1735979.79"));
  }

  private NavFlowReconciliation navFlow(
      BigDecimal unexplained, boolean securityQuantitiesChanged, BigDecimal marketPnl) {
    return navFlow(unexplained, securityQuantitiesChanged, marketPnl, BigDecimal.ZERO);
  }

  private NavFlowReconciliation navFlow(
      BigDecimal unexplained,
      boolean securityQuantitiesChanged,
      BigDecimal marketPnl,
      BigDecimal tradeFlow) {
    return new NavFlowReconciliation(
        new BigDecimal("1126972502.00"),
        new BigDecimal("1142837314.33"),
        marketPnl,
        tradeFlow,
        new BigDecimal("7306865.600"),
        new BigDecimal("11718531.79"),
        new BigDecimal("6418.71"),
        unexplained,
        unexplained.divide(new BigDecimal("1126972502.00"), 6, RoundingMode.HALF_UP),
        securityQuantitiesChanged);
  }

  private SecurityAttribution attribution(String isin, String weightDiff, String securityReturn) {
    return new SecurityAttribution(
        isin,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(weightDiff),
        new BigDecimal(securityReturn),
        null,
        BigDecimal.ZERO);
  }
}
