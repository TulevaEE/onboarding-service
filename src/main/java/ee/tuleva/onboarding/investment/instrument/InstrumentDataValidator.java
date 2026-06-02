package ee.tuleva.onboarding.investment.instrument;

import static java.math.BigDecimal.ONE;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.PositionLimitRepository;
import ee.tuleva.onboarding.investment.portfolio.ProviderLimitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentDataValidator {

  private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.0001");
  private static final int MIN_PRICE_DAYS = 2;

  private final InstrumentReferenceService instrumentReferenceService;
  private final ModelPortfolioAllocationRepository allocationRepository;
  private final PositionLimitRepository positionLimitRepository;
  private final ProviderLimitRepository providerLimitRepository;
  private final FundValueProvider fundValueProvider;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  public List<ValidationFinding> validate(TulevaFund fund, LocalDate effectiveDate) {
    var findings = new ArrayList<ValidationFinding>();
    var allocations = allocationRepository.findByFundAndEffectiveDate(fund, effectiveDate);

    if (allocations.isEmpty()) {
      return findings;
    }

    var isins =
        allocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    checkPriceConfigExists(isins, findings);
    checkWeightsSum(fund, allocations, findings);
    checkPositionLimits(fund, isins, effectiveDate, findings);
    checkProviderLimits(fund, allocations, effectiveDate, findings);
    checkBenchmarkProxies(isins, findings);
    checkActive(isins, findings);
    checkTickerConsistency(allocations, findings);

    if (effectiveDate.isAfter(LocalDate.now(clock))) {
      checkPriceHistory(isins, findings);
    }

    if (!findings.isEmpty()) {
      log.warn(
          "Instrument data validation findings: fund={}, effectiveDate={}, findings={}",
          fund,
          effectiveDate,
          findings);
    }

    return findings;
  }

  private void checkPriceConfigExists(Set<String> isins, List<ValidationFinding> findings) {
    for (var isin : isins) {
      if (instrumentReferenceService.findByIsin(isin).isEmpty()) {
        findings.add(
            new ValidationFinding(
                Severity.FAIL,
                "ISIN %s not in instrument_reference — prices will not be fetched"
                    .formatted(isin)));
      }
    }
  }

  private void checkWeightsSum(
      TulevaFund fund,
      List<ModelPortfolioAllocation> allocations,
      List<ValidationFinding> findings) {
    var totalWeight =
        allocations.stream()
            .map(ModelPortfolioAllocation::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalWeight.subtract(ONE).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      findings.add(
          new ValidationFinding(
              Severity.FAIL,
              "Weight sum for %s is %s, expected 1.0"
                  .formatted(fund, totalWeight.toPlainString())));
    }
  }

  private void checkPositionLimits(
      TulevaFund fund,
      Set<String> isins,
      LocalDate effectiveDate,
      List<ValidationFinding> findings) {
    var limits = positionLimitRepository.findLatestByFundAsOf(fund, effectiveDate);
    var limitIsins = limits.stream().map(l -> l.getIsin()).collect(Collectors.toSet());

    for (var isin : isins) {
      if (!limitIsins.contains(isin)) {
        findings.add(
            new ValidationFinding(
                Severity.FAIL, "No position limit for %s in fund %s".formatted(isin, fund)));
      }
    }
  }

  private void checkProviderLimits(
      TulevaFund fund,
      List<ModelPortfolioAllocation> allocations,
      LocalDate effectiveDate,
      List<ValidationFinding> findings) {
    if (fund != TulevaFund.TKF100) {
      return;
    }

    var providers =
        allocations.stream()
            .map(ModelPortfolioAllocation::getProvider)
            .filter(Objects::nonNull)
            .map(Enum::name)
            .collect(Collectors.toSet());

    var providerLimits = providerLimitRepository.findLatestByFundAsOf(fund, effectiveDate);
    var limitProviders =
        providerLimits.stream().map(l -> l.getProvider().name()).collect(Collectors.toSet());

    for (var provider : providers) {
      if (!limitProviders.contains(provider)) {
        findings.add(
            new ValidationFinding(
                Severity.FAIL, "No provider limit for %s in fund %s".formatted(provider, fund)));
      }
    }
  }

  private void checkBenchmarkProxies(Set<String> isins, List<ValidationFinding> findings) {
    for (var isin : isins) {
      var instrument = instrumentReferenceService.findByIsin(isin).orElse(null);
      if (instrument == null || instrument.getBenchmarkCategory() == null) {
        continue;
      }
      var proxy =
          instrumentReferenceService.resolveBenchmarkProxy(
              instrument.getBenchmarkCategory(), instrument.isExchangeTraded());
      if (proxy.isEmpty()) {
        findings.add(
            new ValidationFinding(
                Severity.WARNING,
                "No benchmark proxy for category %s (ISIN %s) — TD BENCHMARK_MODEL will skip"
                    .formatted(instrument.getBenchmarkCategory(), isin)));
      }
    }
  }

  private void checkActive(Set<String> isins, List<ValidationFinding> findings) {
    for (var isin : isins) {
      var instrument = instrumentReferenceService.findByIsin(isin).orElse(null);
      if (instrument != null && !instrument.isActive()) {
        findings.add(
            new ValidationFinding(
                Severity.FAIL,
                "ISIN %s is active=false in instrument_reference — prices not being fetched"
                    .formatted(isin)));
      }
    }
  }

  private void checkTickerConsistency(
      List<ModelPortfolioAllocation> allocations, List<ValidationFinding> findings) {
    for (var allocation : allocations) {
      if (allocation.getIsin() == null || allocation.getTicker() == null) {
        continue;
      }
      var instrument = instrumentReferenceService.findByIsin(allocation.getIsin()).orElse(null);
      if (instrument != null
          && instrument.getYahooTicker() != null
          && !allocation.getTicker().equals(instrument.getYahooTicker())) {
        findings.add(
            new ValidationFinding(
                Severity.WARNING,
                "Ticker mismatch for %s: allocation=%s, instrument_reference=%s"
                    .formatted(
                        allocation.getIsin(),
                        allocation.getTicker(),
                        instrument.getYahooTicker())));
      }
    }
  }

  private void checkPriceHistory(Set<String> isins, List<ValidationFinding> findings) {
    var today = LocalDate.now(clock);
    for (var isin : isins) {
      var instrument = instrumentReferenceService.findByIsin(isin).orElse(null);
      if (instrument == null) {
        continue;
      }

      int priceDays = countRecentPriceDays(instrument, today);
      if (priceDays < MIN_PRICE_DAYS) {
        findings.add(
            new ValidationFinding(
                Severity.FAIL,
                "ISIN %s has only %d business days of prices (need %d) — not ready for model portfolio"
                    .formatted(isin, priceDays, MIN_PRICE_DAYS)));
      }
    }
  }

  private int countRecentPriceDays(InstrumentReference instrument, LocalDate today) {
    int count = 0;
    var date = today;
    for (int i = 0; i < 10 && count < MIN_PRICE_DAYS; i++) {
      date = publicHolidays.previousWorkingDay(date);
      if (hasAnyPrice(instrument, date)) {
        count++;
      }
    }
    return count;
  }

  private boolean hasAnyPrice(InstrumentReference instrument, LocalDate date) {
    return instrument
            .getXetraStorageKey()
            .flatMap(k -> fundValueProvider.getValueForDate(k, date))
            .isPresent()
        || instrument
            .getEuronextParisStorageKey()
            .flatMap(k -> fundValueProvider.getValueForDate(k, date))
            .isPresent()
        || (instrument.getEodhdTicker() != null
            && fundValueProvider.getValueForDate(instrument.getEodhdTicker(), date).isPresent())
        || (instrument.getYahooTicker() != null
            && fundValueProvider.getValueForDate(instrument.getYahooTicker(), date).isPresent());
  }

  public record ValidationFinding(Severity severity, String message) {}

  public enum Severity {
    FAIL,
    WARNING
  }
}
