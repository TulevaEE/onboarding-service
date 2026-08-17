package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The depot fee follows the Depooleping, not the NAV formula, and differs from the management fee
 * on all three of base, day count and rate.
 *
 * <p><b>Base.</b> "Depootasu arvutatakse igapäevaselt Fondi aktivate turuväärtuste summale vastava
 * päevalõpu seisuga" — the market value of the fund's assets at the corresponding end of day. That
 * is the gross asset side, so {@link FeeBases#assetValue()} rather than the net {@link
 * FeeBases#navFeeBase()} the management fee uses.
 *
 * <p><b>Day count.</b> "kasutades tegeliku (aasta ja kuu) päevade arvu meetodit" — actual/actual.
 * Every calendar day accrues and the divisor is the real length of that year, so 366 in a leap
 * year. {@code FeeAccrualBuilder.DAYS_IN_YEAR} is fixed at 365 and stays in use for the management
 * fee, whose own terms specify it; the next divergence between the two is 2028.
 */
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
    int daysInYear = Year.of(calendarDate.getYear()).length();

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

  /**
   * A row valid on the accrual date decides the rate: a TIER row reads the AUM tier for the fee
   * month, a FIXED row carries the rate itself. No row means no depot fee — never the tier, so that
   * a lapsed or deleted row cannot silently start charging one.
   */
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

  /**
   * The tier band for a fee month, on the funds' combined assets two month ends back.
   *
   * <p>The two-month lag is the agreement's, not a safety margin. The depositary submits, by the
   * 10th of each month, the asset values from the last business day of the preceding month, for the
   * rate applicable the month after: by 10 September it sends end-of-August values, Tuleva confirms
   * by the 20th, and the rate runs from 1 October. So the fee month's band is anchored two month
   * ends back, while the tier row's own validity is tested against the fee month, which is when
   * that rate is the one in force.
   *
   * <p><b>Calculation is automatic, verification is manual.</b> This method is what the accrual
   * uses, deliberately. The depositary also calculates the rate, emails it to funds@tuleva.ee when
   * it changes, and Tuleva confirms it to trustee@seb.ee by the 20th; that exchange is the check on
   * this code, and it is a manual one — no system holds either side of it.
   *
   * <p>So a disagreement between the two is a defect <b>here</b>, in this method or in the {@code
   * investment_depot_fee_tier} rows, and the fix belongs here too. Do not paper over one by
   * hand-entering a FIXED rate: that hides the defect and leaves the next month wrong the same way.
   *
   * <p>Why the check has to be prompt rather than eventual: accruals are forward-only, so days
   * already written cannot be corrected in place. Repairing them means deleting the DEPOT accruals
   * from the affected date and re-running the backfill, and the longer the gap the more days that
   * is.
   */
  private BigDecimal determineDepotRateFromTier(LocalDate feeMonth) {
    LocalDate submissionBasisDate = feeMonth.minusMonths(1).minusDays(1);
    BigDecimal totalAssets = getTotalAssetValue(submissionBasisDate);
    return tierRepository.findRateForAum(totalAssets, feeMonth);
  }

  /**
   * Every fund's assets at its own last calculation on or before the anchor, added up. Resolved per
   * fund rather than off one shared date so that a fund without a calculation on that exact date
   * contributes its latest known assets instead of dropping out of the total and pulling the whole
   * band down.
   */
  private BigDecimal getTotalAssetValue(LocalDate upToDate) {
    BigDecimal total = ZERO;
    for (TulevaFund fund : TulevaFund.values()) {
      Optional<LocalDate> navDate =
          fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), upToDate);
      if (navDate.isEmpty()) {
        continue;
      }
      total =
          total
              .add(fundNavQueryService.findAssetTotal(fund.getCode(), navDate.get()).orElse(ZERO))
              .add(blackrockAdjustment(fund, navDate.get()));
    }
    return total;
  }

  /**
   * The one asset term nav_report cannot answer for. {@code NavReportMapper} writes the BlackRock
   * receivable and liability rows only for pension funds, so for a savings fund the asset total
   * read back from nav_report is short by exactly this adjustment — while the daily base, taken
   * from the NAV components in memory, includes it. Reading it from the ledger brings the two
   * definitions of aktiva back together. Same approach as {@code
   * FeeBaseCompletenessChecker.navFeeBaseBlackrockAdjustment}.
   *
   * <p>Zero for a pension fund, because nav_report already carries the adjustment — though only the
   * part of it that landed on the asset side. {@code NavReportMapper} splits the signed amount, the
   * positive part into a RECEIVABLES row and the negative part into a LIABILITY one, and {@code
   * findAssetTotal} reads the assets alone. So the two definitions coincide only while the
   * adjustment is at or above zero: a pension fund with a <b>negative</b> adjustment gets a tier
   * basis larger than its own daily base by exactly that amount. Both readings are defensible as
   * "aktiva" and neither is obviously the Depooleping's, which is a question worth settling before
   * the account carries anything. Today it is empty for every fund, so this is zero throughout and
   * nothing turns on it.
   */
  private BigDecimal blackrockAdjustment(TulevaFund fund, LocalDate navDate) {
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
