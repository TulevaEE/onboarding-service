package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_FLOOR;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_CREDIT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_PERCENTILE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_OUTFLOW_PERCENTILE;

import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CashBufferParameters {

  private static final List<InvestmentParameter> REVIEW_PARAMETERS =
      List.of(
          CASH_BUFFER_OUTFLOW_PERCENTILE,
          CASH_BUFFER_INFLOW_PERCENTILE,
          CASH_BUFFER_INFLOW_CREDIT,
          CASH_BUFFER_FLOOR,
          CASH_BUFFER_DRIFT_THRESHOLD,
          CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS);

  private final InvestmentParameterRepository repository;

  List<InvestmentParameter> missing(TulevaFund fund, LocalDate asOf) {
    return REVIEW_PARAMETERS.stream()
        .filter(parameter -> fundOrGlobal(parameter, fund, asOf).isEmpty())
        .toList();
  }

  ReviewRules resolve(TulevaFund fund, LocalDate asOf) {
    return new ReviewRules(
        new BufferModel(
            required(CASH_BUFFER_OUTFLOW_PERCENTILE, fund, asOf),
            required(CASH_BUFFER_INFLOW_PERCENTILE, fund, asOf),
            required(CASH_BUFFER_INFLOW_CREDIT, fund, asOf),
            required(CASH_BUFFER_FLOOR, fund, asOf)),
        new DriftRule(
            required(CASH_BUFFER_DRIFT_THRESHOLD, fund, asOf),
            required(CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS, fund, asOf).intValueExact()));
  }

  private BigDecimal required(InvestmentParameter parameter, TulevaFund fund, LocalDate asOf) {
    return fundOrGlobal(parameter, fund, asOf)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Cash buffer parameter missing: parameter="
                        + parameter
                        + ", fund="
                        + fund
                        + ", asOf="
                        + asOf));
  }

  private Optional<BigDecimal> fundOrGlobal(
      InvestmentParameter parameter, TulevaFund fund, LocalDate asOf) {
    return repository
        .findLatestValueIfPresent(parameter, fund, asOf)
        .or(() -> repository.findLatestValueIfPresent(parameter, asOf));
  }
}
