package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepotRateResolver {

  private final DepotFeeTierRepository tierRepository;
  private final FeeRateRepository feeRateRepository;
  private final FundNavQueryService fundNavQueryService;
  private final FeeMonthResolver feeMonthResolver;
  private final PublishedFeeBase publishedFeeBase;

  public DepotRate resolveRate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    Optional<FeeRate> rate = feeRateRepository.findValidRate(fund, DEPOT, calendarDate);
    if (rate.isEmpty()) {
      log.warn(
          "No depot fee rate configured, resolving zero: fund={}, date={}", fund, calendarDate);
      return DepotRate.none();
    }
    return rate.get().isTierBased()
        ? rateFromTier(feeMonth)
        : DepotRate.flat(rate.get().annualRate());
  }

  private DepotRate rateFromTier(LocalDate feeMonth) {
    LocalDate anchor = twoMonthEndsBefore(feeMonth);
    Optional<BigDecimal> totalFeeBase = combinedFeeBaseAt(anchor);
    if (totalFeeBase.isEmpty()) {
      log.warn(
          "No published fee base behind the depot tier, resolving zero: feeMonth={}, anchor={}",
          feeMonth,
          anchor);
      return new DepotRate(ZERO, anchor, null);
    }
    Optional<BigDecimal> tierRate = tierRepository.findRateForAum(totalFeeBase.get(), feeMonth);
    if (tierRate.isEmpty()) {
      log.warn(
          "No depot fee tier configured, resolving zero: totalFeeBase={}, feeMonth={}",
          totalFeeBase.get(),
          feeMonth);
      return new DepotRate(ZERO, anchor, totalFeeBase.get());
    }
    return new DepotRate(tierRate.get(), anchor, totalFeeBase.get());
  }

  private static LocalDate twoMonthEndsBefore(LocalDate feeMonth) {
    return feeMonth.minusMonths(1).minusDays(1);
  }

  private Optional<BigDecimal> combinedFeeBaseAt(LocalDate anchor) {
    return Arrays.stream(TulevaFund.values())
        .map(fund -> publishedFeeBaseAtAnchor(fund, anchor))
        .reduce(Optional.of(ZERO), (total, base) -> total.flatMap(sum -> base.map(sum::add)));
  }

  private Optional<BigDecimal> publishedFeeBaseAtAnchor(TulevaFund fund, LocalDate anchor) {
    if (hasNotLaunchedBy(fund, anchor)) {
      return Optional.of(ZERO);
    }
    return fundNavQueryService
        .findLatestPublishedNavDateOnOrBefore(fund.getCode(), anchor)
        .flatMap(navDate -> publishedFeeBase.find(fund, navDate));
  }

  private boolean hasNotLaunchedBy(TulevaFund fund, LocalDate anchor) {
    return fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), anchor).isEmpty();
  }
}
