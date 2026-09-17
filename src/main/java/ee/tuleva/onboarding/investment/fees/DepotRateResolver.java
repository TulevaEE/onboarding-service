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

  public BigDecimal resolveAnnualRate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    Optional<FeeRate> rate = feeRateRepository.findValidRate(fund, DEPOT, calendarDate);
    if (rate.isEmpty()) {
      log.warn(
          "No depot fee rate configured, resolving zero: fund={}, date={}", fund, calendarDate);
      return ZERO;
    }
    return rate.get().isTierBased() ? rateFromTier(feeMonth) : rate.get().annualRate();
  }

  private BigDecimal rateFromTier(LocalDate feeMonth) {
    BigDecimal totalAssets = combinedFundAssetsTwoMonthEndsBefore(feeMonth);
    Optional<BigDecimal> tierRate = tierRepository.findRateForAum(totalAssets, feeMonth);
    if (tierRate.isEmpty()) {
      log.warn(
          "No depot fee tier configured, resolving zero: totalAssets={}, feeMonth={}",
          totalAssets,
          feeMonth);
      return ZERO;
    }
    return tierRate.get();
  }

  private BigDecimal combinedFundAssetsTwoMonthEndsBefore(LocalDate feeMonth) {
    LocalDate anchor = feeMonth.minusMonths(1).minusDays(1);
    BigDecimal total = ZERO;
    for (TulevaFund fund : TulevaFund.values()) {
      total = total.add(assetsAtLatestCalculationOnOrBefore(fund, anchor));
    }
    return total;
  }

  private BigDecimal assetsAtLatestCalculationOnOrBefore(TulevaFund fund, LocalDate anchor) {
    Optional<LocalDate> navDate =
        fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), anchor);
    if (navDate.isEmpty()) {
      return ZERO;
    }
    BigDecimal assets =
        fundNavQueryService.findAssetTotal(fund.getCode(), navDate.get()).orElse(ZERO);
    return assets.add(savingsFundBlackrockAdjustment(fund, navDate.get()));
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
