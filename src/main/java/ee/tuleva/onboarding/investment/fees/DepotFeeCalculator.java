package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
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

  private final DepotFeeTierRepository tierRepository;
  private final FundNavQueryService fundNavQueryService;
  private final FeeMonthResolver feeMonthResolver;
  private final FeeRateRepository feeRateRepository;

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
   * <p><b>This is a recomputation, not the source.</b> Contractually the depositary calculates the
   * rate, sends it to funds@tuleva.ee only when it differs from the one in force, and Tuleva
   * confirms it to trustee@seb.ee by the 20th. The rate is therefore sticky between notifications
   * and the confirmed one governs. That exchange is a manual process today — it happens over email
   * and no system holds it — so what this method returns has nothing to agree against inside the
   * application.
   *
   * <p>The failure that follows: in a month where our assets cross a band and no notification
   * arrived, this silently moves the accrual away from the rate actually agreed. Accruals are
   * forward-only, so by the time anyone compares, the affected days cannot be rewritten without
   * deleting them and re-running the backfill. If a rate change here does not correspond to a
   * notification you can point at, that is the bug, not the notification.
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
          total.add(fundNavQueryService.findAssetTotal(fund.getCode(), navDate.get()).orElse(ZERO));
    }
    return total;
  }
}
