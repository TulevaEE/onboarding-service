package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record PevaRavaStatus(
    PevaRavaPhase phase,
    @Nullable PevaRavaCycle cycle,
    @Nullable FundCycleTimeline tuk75,
    @Nullable FundCycleTimeline tuk00,
    Map<TulevaFund, PevaRavaFlows> flows) {}
