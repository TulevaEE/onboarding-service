package ee.tuleva.onboarding.investment.kid.performance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record FundBenchmarks(Map<TulevaFund, Benchmark> benchmarks) {

  private static final String MSCI_ACWI = "MSCI_ACWI";
  private static final EquityCap TUK75_HELD_AT_75_PERCENT_EQUITY_UNTIL_2_SEPTEMBER_2019 =
      new EquityCap("EURO_AGGREGATE_BOND", new BigDecimal("0.75"), LocalDate.of(2019, 9, 2));

  static final FundBenchmarks TULEVA_FUNDS =
      new FundBenchmarks(
          Map.of(
              TKF100, new Benchmark(MSCI_ACWI, null),
              TUK75,
                  new Benchmark(MSCI_ACWI, TUK75_HELD_AT_75_PERCENT_EQUITY_UNTIL_2_SEPTEMBER_2019),
              TUV100, new Benchmark(MSCI_ACWI, null)));

  FundBenchmarks {
    benchmarks = Map.copyOf(benchmarks);
  }

  @Nullable Benchmark benchmarkFor(TulevaFund fund) {
    return benchmarks.get(fund);
  }
}
