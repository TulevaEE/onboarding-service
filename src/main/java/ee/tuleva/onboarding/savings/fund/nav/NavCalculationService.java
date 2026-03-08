package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
import ee.tuleva.onboarding.investment.fees.FeeResult;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import ee.tuleva.onboarding.savings.fund.nav.components.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavCalculationService {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundPositionRepository fundPositionRepository;
  private final PublicHolidays publicHolidays;
  private final LedgerService ledgerService;
  private final NavLedgerRepository navLedgerRepository;
  private final SecuritiesValueComponent securitiesValueComponent;
  private final CashPositionComponent cashPositionComponent;
  private final ReceivablesComponent receivablesComponent;
  private final PayablesComponent payablesComponent;
  private final SubscriptionsComponent subscriptionsComponent;
  private final RedemptionsComponent redemptionsComponent;
  private final BlackrockAdjustmentComponent blackrockAdjustmentComponent;
  private final PositionPriceResolver positionPriceResolver;
  private final FeeCalculationService feeCalculationService;
  private final Clock clock;

  @Transactional
  public NavCalculationResult calculate(String fundCode, LocalDate calculationDate) {
    return calculate(TulevaFund.fromCode(fundCode), calculationDate);
  }

  @Transactional
  public NavCalculationResult calculate(TulevaFund fund, LocalDate calculationDate) {
    log.info("Starting NAV calculation: fund={}, date={}", fund, calculationDate);

    LocalDate positionReportDate = getPositionReportDate(fund, calculationDate);
    if (positionReportDate == null) {
      throw new IllegalStateException(
          "No position report found: fund=" + fund + ", date=" + calculationDate);
    }

    Instant cutoff =
        calculationDate.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();

    NavComponentContext context =
        NavComponentContext.builder()
            .fund(fund)
            .calculationDate(calculationDate)
            .positionReportDate(positionReportDate)
            .priceDate(positionReportDate)
            .cutoff(cutoff)
            .build();

    BigDecimal securitiesValue = securitiesValueComponent.calculate(context);
    BigDecimal cashPosition = cashPositionComponent.calculate(context);
    BigDecimal receivables = receivablesComponent.calculate(context);
    BigDecimal payables = payablesComponent.calculate(context);
    BigDecimal pendingSubscriptions = subscriptionsComponent.calculate(context);

    BigDecimal feeBaseValue = securitiesValue.add(cashPosition).add(receivables).subtract(payables);
    Instant feeCutoff = positionReportDate.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    FeeResult fees =
        feeCalculationService.calculateFeesForNav(
            fund, positionReportDate, feeBaseValue, feeCutoff, context.getSecurityPrices());
    BigDecimal managementFeeAccrual = fees.managementFeeAccrual();
    BigDecimal depotFeeAccrual = fees.depotFeeAccrual();

    BigDecimal blackrockAdjustment = blackrockAdjustmentComponent.calculate(context);

    BigDecimal unitsOutstanding = getUnitsOutstanding(fund, calculationDate);
    context.setUnitsOutstanding(unitsOutstanding);

    BigDecimal pendingRedemptions = ZERO;
    if (unitsOutstanding.signum() > 0) {
      pendingRedemptions = redemptionsComponent.calculate(context);
    }

    BigDecimal aum =
        calculateAum(
            securitiesValue,
            cashPosition,
            receivables,
            payables,
            pendingSubscriptions,
            pendingRedemptions,
            managementFeeAccrual,
            depotFeeAccrual,
            blackrockAdjustment);
    BigDecimal navPerUnit = calculateNavPerUnit(aum, unitsOutstanding, fund);

    NavCalculationResult result =
        NavCalculationResult.builder()
            .fund(fund)
            .calculationDate(calculationDate)
            .securitiesValue(securitiesValue)
            .cashPosition(cashPosition)
            .receivables(receivables)
            .pendingSubscriptions(pendingSubscriptions)
            .pendingRedemptions(pendingRedemptions)
            .managementFeeAccrual(managementFeeAccrual)
            .depotFeeAccrual(depotFeeAccrual)
            .payables(payables)
            .blackrockAdjustment(blackrockAdjustment)
            .aum(aum)
            .unitsOutstanding(unitsOutstanding)
            .navPerUnit(navPerUnit)
            .positionReportDate(positionReportDate)
            .priceDate(positionReportDate)
            .calculatedAt(Instant.now(clock))
            .securitiesDetail(buildSecuritiesDetail(fund, cutoff, positionReportDate))
            .build();

    validateResult(result);

    log.info(
        "Completed NAV calculation: fund={}, date={}, aum={}, navPerUnit={}",
        fund,
        calculationDate,
        aum,
        navPerUnit);

    return result;
  }

  private LocalDate getPositionReportDate(TulevaFund fund, LocalDate calculationDate) {
    LocalDate expectedDate =
        calculationDate.equals(fund.getInceptionDate())
            ? calculationDate
            : publicHolidays.previousWorkingDay(calculationDate);
    LocalDate actual =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, expectedDate).orElse(null);
    if (actual != null && !actual.equals(expectedDate)) {
      throw new IllegalStateException(
          "Position data missing: fund="
              + fund
              + ", expected="
              + expectedDate
              + ", latest="
              + actual);
    }
    return actual;
  }

  private BigDecimal getUnitsOutstanding(TulevaFund fund, LocalDate calculationDate) {
    Instant cutoff =
        calculationDate.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();
    var account = ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, fund);
    BigDecimal balance = account.getBalanceAt(cutoff);
    if (balance.signum() < 0) {
      throw new IllegalStateException(
          "FUND_UNITS_OUTSTANDING should be positive, but was: " + balance);
    }
    return balance;
  }

  private BigDecimal calculateAum(
      BigDecimal securitiesValue,
      BigDecimal cashPosition,
      BigDecimal receivables,
      BigDecimal payables,
      BigDecimal pendingSubscriptions,
      BigDecimal pendingRedemptions,
      BigDecimal managementFeeAccrual,
      BigDecimal depotFeeAccrual,
      BigDecimal blackrockAdjustment) {

    BigDecimal assets =
        securitiesValue
            .add(cashPosition)
            .add(receivables)
            .add(pendingSubscriptions)
            .add(blackrockAdjustment.max(ZERO));

    BigDecimal liabilities =
        payables
            .add(pendingRedemptions)
            .add(managementFeeAccrual)
            .add(depotFeeAccrual)
            .add(blackrockAdjustment.min(ZERO).negate());

    return assets.subtract(liabilities);
  }

  private BigDecimal calculateNavPerUnit(
      BigDecimal aum, BigDecimal unitsOutstanding, TulevaFund fund) {
    if (unitsOutstanding.signum() == 0) {
      return BigDecimal.ONE;
    }
    return aum.divide(unitsOutstanding, fund.getNavScale(), HALF_UP);
  }

  private List<SecurityDetail> buildSecuritiesDetail(
      TulevaFund fund, Instant cutoff, LocalDate priceDate) {
    return new TreeMap<>(navLedgerRepository.getSecuritiesUnitBalancesAt(cutoff, fund))
        .entrySet().stream()
            .map(
                entry -> {
                  String isin = entry.getKey();
                  BigDecimal units = entry.getValue();
                  var resolvedPrice = positionPriceResolver.resolve(isin, priceDate, cutoff);
                  String ticker = resolvedPrice.map(ResolvedPrice::storageKey).orElse("UNKNOWN");
                  BigDecimal price = resolvedPrice.map(ResolvedPrice::usedPrice).orElse(null);
                  LocalDate resolvedPriceDate =
                      resolvedPrice.map(ResolvedPrice::priceDate).orElse(null);
                  BigDecimal marketValue =
                      price != null ? units.multiply(price).setScale(2, HALF_UP) : null;
                  return new SecurityDetail(
                      isin, ticker, units, price, marketValue, resolvedPriceDate);
                })
            .toList();
  }

  private BigDecimal calculateFeeBaseValue(NavComponentContext context) {
    BigDecimal securitiesValue = securitiesValueComponent.calculate(context);
    BigDecimal cashPosition = cashPositionComponent.calculate(context);
    BigDecimal receivables = receivablesComponent.calculate(context);
    BigDecimal payables = payablesComponent.calculate(context);
    return securitiesValue.add(cashPosition).add(receivables).subtract(payables);
  }

  public record FeeBaseValueResult(
      BigDecimal baseValue,
      LocalDate positionReportDate,
      Map<String, ResolvedPrice> securityPrices) {}

  public Optional<FeeBaseValueResult> computeFeeBaseValue(TulevaFund fund, LocalDate navDate) {
    LocalDate actual =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, navDate).orElse(null);
    if (actual == null) {
      return Optional.empty();
    }
    if (!actual.equals(navDate)) {
      log.error("Position data mismatch: fund={}, expected={}, latest={}", fund, navDate, actual);
      return Optional.empty();
    }

    Instant cutoff = navDate.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();
    NavComponentContext context =
        NavComponentContext.builder()
            .fund(fund)
            .calculationDate(navDate)
            .positionReportDate(navDate)
            .priceDate(navDate)
            .cutoff(cutoff)
            .build();
    BigDecimal baseValue = calculateFeeBaseValue(context);
    return Optional.of(new FeeBaseValueResult(baseValue, navDate, context.getSecurityPrices()));
  }

  @Transactional
  public void backfillFees(TulevaFund fund, LocalDate from, LocalDate to) {
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      LocalDate navDate =
          publicHolidays.isWorkingDay(date) ? date : publicHolidays.previousWorkingDay(date);
      var optional = computeFeeBaseValue(fund, navDate);
      if (optional.isEmpty()) {
        continue;
      }
      log.info("Backfilling fees: fund={}, date={}, navDate={}", fund, date, navDate);
      var result = optional.get();
      Instant feeCutoff = date.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();
      feeCalculationService.calculateFeesForNav(
          fund, date, result.baseValue(), feeCutoff, result.securityPrices());
    }
    log.info("Fee backfill completed: fund={}, from={}, to={}", fund, from, to);
  }

  private void validateResult(NavCalculationResult result) {
    if (result.navPerUnit().signum() <= 0) {
      throw new IllegalStateException(
          "NAV per unit must be positive: navPerUnit=" + result.navPerUnit());
    }
  }
}
