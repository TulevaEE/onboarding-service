package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepotFeeCalculator implements FeeCalculator {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final DepotFeeTierRepository tierRepository;
  private final FundNavQueryService fundNavQueryService;
  private final FeeMonthResolver feeMonthResolver;
  private final FeeRateRepository feeRateRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, FeeBases bases) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);

    BigDecimal annualRate = determineDepotRate(fund, calendarDate, feeMonth);
    BigDecimal assetValue = bases.assetValue();
    int daysInYear = actualDaysInYear(calendarDate);

    BigDecimal dailyFee =
        assetValue.multiply(annualRate).divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(DEPOT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(assetValue)
        .annualRate(annualRate)
        .dailyAmountGross(dailyFee)
        .daysInYear(daysInYear)
        .referenceDate(calendarDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return DEPOT;
  }

  private int actualDaysInYear(LocalDate calendarDate) {
    return Year.of(calendarDate.getYear()).length();
  }

  private BigDecimal determineDepotRate(
      TulevaFund fund, LocalDate calendarDate, LocalDate feeMonth) {
    Optional<FeeRate> rate = feeRateRepository.findValidRate(fund, DEPOT, calendarDate);
    if (rate.isEmpty()) {
      log.warn("No depot fee rate configured, accruing zero: fund={}, date={}", fund, calendarDate);
      return ZERO;
    }
    return rate.get().isTierBased()
        ? determineDepotRateFromTier(feeMonth)
        : rate.get().annualRate();
  }

  private BigDecimal determineDepotRateFromTier(LocalDate feeMonth) {
    Optional<BigDecimal> totalAssets = combinedFundAssetsTwoMonthEndsBefore(feeMonth);
    if (totalAssets.isEmpty()) {
      log.warn("No published assets behind the depot tier, accruing zero: feeMonth={}", feeMonth);
      return ZERO;
    }
    Optional<BigDecimal> tierRate = tierRepository.findRateForAum(totalAssets.get(), feeMonth);
    if (tierRate.isEmpty()) {
      log.warn(
          "No depot fee tier configured, accruing zero: totalAssets={}, feeMonth={}",
          totalAssets.get(),
          feeMonth);
      return ZERO;
    }
    return tierRate.get();
  }

  // A fund short of its published month end leaves the band unknown, not lower: summing what we do
  // have would read that fund as worth nothing and could charge every fund the wrong rate.
  private Optional<BigDecimal> combinedFundAssetsTwoMonthEndsBefore(LocalDate feeMonth) {
    LocalDate anchor = feeMonth.minusMonths(1).minusDays(1);
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
