package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.IGNORE;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PevaRavaStatusService {

  private final PevaRavaPeriodService periodService;
  private final PevaRavaFlowService flowService;
  private final Clock clock;

  public PevaRavaStatus status() {
    LocalDate today = LocalDate.now(clock);
    return periodService
        .getCurrentPeriod(today)
        .map(
            period ->
                new PevaRavaStatus(
                    period.phase(),
                    period.cycle(),
                    period.tuk75(),
                    period.tuk00(),
                    flowService.calculateFlows(today)))
        .orElseGet(() -> new PevaRavaStatus(IGNORE, null, null, null, Map.of()));
  }

  public Map<TulevaFund, PevaRavaFlows> recalculate() {
    return flowService.calculateFlows(LocalDate.now(clock));
  }
}
