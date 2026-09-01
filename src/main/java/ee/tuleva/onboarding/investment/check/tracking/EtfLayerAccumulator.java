package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
final class EtfLayerAccumulator {

  private static final int SCALE = TdAttributionCalculator.SCALE;
  private final BenchmarkLegResolver benchmarkLegResolver;
  private final TulevaFund fund;
  private final Set<String> measuredIsins;
  private final Map<String, BigDecimal> rateByIsin;

  private BigDecimal heldOcf = ZERO;
  private BigDecimal proxyOcf = ZERO;
  private BigDecimal unbenchmarkedWeight = ZERO;
  private BigDecimal unrestoredProxyWeight = ZERO;
  private final Set<String> unpricedIsins = new LinkedHashSet<>();
  private final Set<String> unpricedProxyIsins = new LinkedHashSet<>();

  EtfLayerAccumulator(
      BenchmarkLegResolver benchmarkLegResolver,
      TulevaFund fund,
      Set<String> measuredIsins,
      Map<String, BigDecimal> rateByIsin) {
    this.benchmarkLegResolver = benchmarkLegResolver;
    this.fund = fund;
    this.measuredIsins = measuredIsins;
    this.rateByIsin = rateByIsin;
  }

  void accumulate(ModelPortfolioAllocation allocation) {
    var weight = allocation.getWeight();
    var isin = allocation.getIsin();

    if (!measuredIsins.contains(isin)) {
      unbenchmarkedWeight = unbenchmarkedWeight.add(weight);
      return;
    }

    accumulateHeldOcf(isin, weight);
    accumulateProxyOcf(isin, weight);
  }

  private void accumulateHeldOcf(@Nullable String isin, BigDecimal weight) {
    var ocf = rateByIsin.get(isin);
    if (ocf == null) {
      unpricedIsins.add(isin);
    } else {
      heldOcf = heldOcf.add(weight.multiply(ocf));
    }
  }

  private void accumulateProxyOcf(@Nullable String isin, BigDecimal weight) {
    var proxyInstrument =
        benchmarkLegResolver
            .resolve(Objects.requireNonNull(isin, "Measured allocation missing isin: fund=" + fund))
            .map(BenchmarkProxy::proxyInstrument)
            .orElse(null);
    if (proxyInstrument == null) {
      return;
    }
    var proxyIsin = proxyInstrument.getIsin();
    var proxyRate = rateByIsin.get(proxyIsin);
    if (proxyRate == null) {
      unpricedProxyIsins.add(proxyIsin);
      unrestoredProxyWeight = unrestoredProxyWeight.add(weight);
      return;
    }
    proxyOcf = proxyOcf.add(weight.multiply(proxyRate));
  }

  void logWarnings(LocalDate periodEnd) {
    if (!unpricedIsins.isEmpty()) {
      log.warn(
          "No OCF rate for model instruments, their cost is missing from etf_ocf_drag and falls into the residual: fund={}, asOf={}, isins={}",
          fund,
          periodEnd,
          unpricedIsins);
    }
    if (!unpricedProxyIsins.isEmpty()) {
      log.warn(
          "No OCF rate for benchmark proxy ETFs, so td_vs_benchmark measures against the proxies rather than the index for their share: fund={}, asOf={}, isins={}",
          fund,
          periodEnd,
          unpricedProxyIsins);
    }
    if (unbenchmarkedWeight.signum() > 0) {
      log.warn(
          "Model weight outside the measured ETF layer, its OCF drag and tracking residual use different weight bases: fund={}, asOf={}, unbenchmarkedWeight={}",
          fund,
          periodEnd,
          unbenchmarkedWeight);
    }
  }

  EtfLayer toEtfLayer(BigDecimal measuredSum, int coveredDays) {
    return new EtfLayer(
        measuredSum,
        annualisedDrag(heldOcf, coveredDays),
        annualisedDrag(proxyOcf, coveredDays),
        coveredDays,
        unbenchmarkedWeight,
        unrestoredProxyWeight);
  }

  private static BigDecimal annualisedDrag(BigDecimal weightedRate, int days) {
    return weightedRate
        .negate()
        .multiply(BigDecimal.valueOf(days))
        .divide(BigDecimal.valueOf(365), SCALE, HALF_UP);
  }
}
