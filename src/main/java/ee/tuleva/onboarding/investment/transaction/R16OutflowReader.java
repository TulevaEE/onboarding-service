package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_ROUNDING_STEP;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16FundFlow;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
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
class R16OutflowReader {

  private static final Set<TulevaFund> R16_FUNDS = Set.of(TUK75, TUK00, TUV100);

  private final R16FlowCalculationService r16FlowCalculationService;
  private final R16PhaseCalculator r16PhaseCalculator;
  private final InvestmentParameterRepository investmentParameterRepository;

  BigDecimal read(TulevaFund fund, LocalDate asOfDate) {
    if (!R16_FUNDS.contains(fund)) {
      return ZERO;
    }
    Optional<R16FundFlow> flow = r16FlowCalculationService.calculateFlows(fund, asOfDate);
    if (flow.isEmpty()) {
      return ZERO;
    }
    return outflow(flow.get(), asOfDate);
  }

  private BigDecimal outflow(R16FundFlow flow, LocalDate asOfDate) {
    return switch (r16PhaseCalculator.phaseFor(flow, asOfDate)) {
      case ACTIVE -> flow.totalOutflowEur().abs();
      case BUFFERED -> bufferedOutflow(flow.totalOutflowEur(), asOfDate);
      case IGNORE, VISIBLE -> ZERO;
    };
  }

  private BigDecimal bufferedOutflow(BigDecimal totalOutflowEur, LocalDate asOfDate) {
    BigDecimal bufferPercent =
        investmentParameterRepository.findLatestValue(R16_BUFFER_PERCENT, asOfDate);
    BigDecimal step = investmentParameterRepository.findLatestValue(R16_ROUNDING_STEP, asOfDate);
    if (step.signum() <= 0) {
      throw new IllegalStateException(
          "Invalid investment parameter: parameter=R16_ROUNDING_STEP, value="
              + step.toPlainString());
    }
    return totalOutflowEur
        .abs()
        .multiply(ONE.add(bufferPercent))
        .divide(step, 0, CEILING)
        .multiply(step);
  }
}
