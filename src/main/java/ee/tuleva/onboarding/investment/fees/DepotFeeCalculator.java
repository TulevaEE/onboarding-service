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
    BigDecimal totalAssets = combinedFundAssetsTwoMonthEndsBefore(feeMonth);
    Optional<BigDecimal> tierRate = tierRepository.findRateForAum(totalAssets, feeMonth);
    if (tierRate.isEmpty()) {
      log.warn(
          "No depot fee tier configured, accruing zero: totalAssets={}, feeMonth={}",
          totalAssets,
          feeMonth);
      return ZERO;
    }
    return tierRate.get();
  }

  private BigDecimal combinedFundAssetsTwoMonthEndsBefore(LocalDate feeMonth) {
    LocalDate anchor = feeMonth.minusMonths(1).minusDays(1);
    return Arrays.stream(TulevaFund.values())
        .map(fund -> assetsAtLatestCalculationOnOrBefore(fund, anchor))
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal assetsAtLatestCalculationOnOrBefore(TulevaFund fund, LocalDate anchor) {
    return fundNavQueryService
        .findLatestNavDateOnOrBefore(fund.getCode(), anchor)
        .map(
            navDate ->
                fundNavQueryService
                    .findAssetTotal(fund.getCode(), navDate)
                    .orElse(ZERO)
                    .add(savingsFundBlackrockAdjustment(fund, navDate)))
        .orElse(ZERO);
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
