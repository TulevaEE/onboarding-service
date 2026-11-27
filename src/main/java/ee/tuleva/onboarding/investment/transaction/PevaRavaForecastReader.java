package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.epis.FundCycleTimeline;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlowService;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class PevaRavaForecastReader {

  private static final Set<TulevaFund> PEVA_RAVA_FUNDS = Set.of(TUK75, TUK00);

  private final PevaRavaPeriodService pevaRavaPeriodService;
  private final PevaRavaFlowService pevaRavaFlowService;

  Optional<BigDecimal> liquidityInAnActiveCycle(TulevaFund fund, LocalDate asOfDate) {
    if (!PEVA_RAVA_FUNDS.contains(fund)) {
      return Optional.empty();
    }
    return pevaRavaPeriodService
        .getCurrentPeriod(asOfDate)
        .filter(period -> period.countsFlowsOf(fund))
        .map(
            period ->
                Optional.ofNullable(pevaRavaFlowService.calculateFlows(asOfDate).get(fund))
                    .map(flows -> liquidityInForce(flows, period.timelineFor(fund)))
                    .orElse(ZERO));
  }

  private static BigDecimal liquidityInForce(PevaRavaFlows flows, FundCycleTimeline timeline) {
    return timeline.sellByReached() ? flows.tradeBufferedLiquidity() : flows.liquidityRequired();
  }
}
