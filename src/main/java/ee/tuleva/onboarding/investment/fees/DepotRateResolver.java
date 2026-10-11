package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepotRateResolver {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final DepotFeeTierRepository tierRepository;
  private final FeeRateRepository feeRateRepository;
  private final FundNavQueryService fundNavQueryService;
  private final FeeMonthResolver feeMonthResolver;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;

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
    Optional<BigDecimal> totalAssets = combinedFundAssetsAt(anchor);
    if (totalAssets.isEmpty()) {
      log.warn(
          "No published assets behind the depot tier, resolving zero: feeMonth={}, anchor={}",
          feeMonth,
          anchor);
      return new DepotRate(ZERO, anchor, null);
    }
    Optional<BigDecimal> tierRate = tierRepository.findRateForAum(totalAssets.get(), feeMonth);
    if (tierRate.isEmpty()) {
      log.warn(
          "No depot fee tier configured, resolving zero: totalAssets={}, feeMonth={}",
          totalAssets.get(),
          feeMonth);
      return new DepotRate(ZERO, anchor, totalAssets.get());
    }
    return new DepotRate(tierRate.get(), anchor, totalAssets.get());
  }

  private static LocalDate twoMonthEndsBefore(LocalDate feeMonth) {
    return feeMonth.minusMonths(1).minusDays(1);
  }

  private Optional<BigDecimal> combinedFundAssetsAt(LocalDate anchor) {
    return Arrays.stream(TulevaFund.values())
        .map(fund -> publishedAssetsAtAnchor(fund, anchor))
        .reduce(Optional.of(ZERO), (total, assets) -> total.flatMap(sum -> assets.map(sum::add)));
  }

  private Optional<BigDecimal> publishedAssetsAtAnchor(TulevaFund fund, LocalDate anchor) {
    if (hasNotLaunchedBy(fund, anchor)) {
      return Optional.of(ZERO);
    }
    return fundNavQueryService
        .findLatestPublishedNavDateOnOrBefore(fund.getCode(), anchor)
        .flatMap(navDate -> publishedAssetsOn(fund, navDate));
  }

  private boolean hasNotLaunchedBy(TulevaFund fund, LocalDate anchor) {
    return fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), anchor).isEmpty();
  }

  private Optional<BigDecimal> publishedAssetsOn(TulevaFund fund, LocalDate navDate) {
    return fundNavQueryService
        .findAssetTotal(fund.getCode(), navDate)
        .map(assets -> assets.add(savingsFundBlackrockAdjustment(fund, navDate)));
  }

  private BigDecimal savingsFundBlackrockAdjustment(TulevaFund fund, LocalDate navDate) {
    if (!fund.isSavingsFund()) {
      return ZERO;
    }
    Instant cutoff =
        publicHolidays
            .nextWorkingDay(navDate)
            .atTime(fund.getNavCutoffTime())
            .atZone(ESTONIAN_ZONE)
            .toInstant();
    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            SystemAccount.BLACKROCK_ADJUSTMENT.getAccountName(fund), cutoff);
    return balance == null ? ZERO : balance;
  }
}
