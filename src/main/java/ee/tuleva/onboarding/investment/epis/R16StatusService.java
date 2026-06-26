package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class R16StatusService {

  private static final List<TulevaFund> R16_FUNDS =
      List.of(TulevaFund.TUK75, TulevaFund.TUK00, TulevaFund.TUV100);

  private final R16FlowCalculationService flowCalculationService;
  private final R16PhaseCalculator phaseCalculator;
  private final Clock clock;

  public List<R16FundStatus> status() {
    LocalDate today = LocalDate.now(clock);
    return R16_FUNDS.stream().map(fund -> fundStatus(fund, today)).toList();
  }

  private R16FundStatus fundStatus(TulevaFund fund, LocalDate today) {
    Optional<R16FundFlow> flow = flowCalculationService.calculateFlows(fund, today);
    R16Phase phase = phaseCalculator.phaseFor(flow.orElse(null), today);
    return flow.map(fundFlow -> toStatus(fundFlow, phase))
        .orElseGet(() -> new R16FundStatus(fund, phase, null, null, null, null, null, null, false));
  }

  private R16FundStatus toStatus(R16FundFlow flow, R16Phase phase) {
    return new R16FundStatus(
        flow.fund(),
        phase,
        flow.fondimaksedUnits(),
        flow.uhekordsedUnits(),
        flow.totalOutflowEur(),
        flow.paymentMonth(),
        flow.paymentDeadline(),
        flow.sellByDate(),
        phaseCalculator.isSuppressedByR45(flow));
  }
}
