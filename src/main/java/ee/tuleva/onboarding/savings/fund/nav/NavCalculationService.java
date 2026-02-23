package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import ee.tuleva.onboarding.savings.fund.nav.components.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavCalculationService {

  private static final int NAV_PRECISION = 4;
  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0);
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundPositionRepository fundPositionRepository;
  private final LedgerService ledgerService;
  private final NavLedgerRepository navLedgerRepository;
  private final SecuritiesValueComponent securitiesValueComponent;
  private final CashPositionComponent cashPositionComponent;
  private final ReceivablesComponent receivablesComponent;
  private final PayablesComponent payablesComponent;
  private final SubscriptionsComponent subscriptionsComponent;
  private final RedemptionsComponent redemptionsComponent;
  private final ManagementFeeAccrualComponent managementFeeAccrualComponent;
  private final DepotFeeAccrualComponent depotFeeAccrualComponent;
  private final BlackrockAdjustmentComponent blackrockAdjustmentComponent;
  private final PositionPriceResolver positionPriceResolver;
  private final Clock clock;

  public NavCalculationResult calculate(String fundCode, LocalDate calculationDate) {
    return calculate(TulevaFund.fromCode(fundCode), calculationDate);
  }

  public NavCalculationResult calculate(TulevaFund fund, LocalDate calculationDate) {
    log.info("Starting NAV calculation: fund={}, date={}", fund, calculationDate);

    LocalDate positionReportDate = getPositionReportDate(fund, calculationDate);
    if (positionReportDate == null) {
      throw new IllegalStateException(
          "No position report found: fund=" + fund + ", date=" + calculationDate);
    }

    NavComponentContext context =
        NavComponentContext.builder()
            .fund(fund)
            .calculationDate(calculationDate)
            .positionReportDate(positionReportDate)
            .priceDate(calculationDate)
            .build();

    // TODO: are all these components tracked in the ledger?
    BigDecimal securitiesValue = securitiesValueComponent.calculate(context);
    BigDecimal cashPosition = cashPositionComponent.calculate(context);
    BigDecimal receivables = receivablesComponent.calculate(context);
    BigDecimal payables = payablesComponent.calculate(context);
    BigDecimal pendingSubscriptions = subscriptionsComponent.calculate(context);
    BigDecimal managementFeeAccrual = managementFeeAccrualComponent.calculate(context);
    BigDecimal depotFeeAccrual = depotFeeAccrualComponent.calculate(context);
    BigDecimal blackrockAdjustment = blackrockAdjustmentComponent.calculate(context);

    BigDecimal unitsOutstanding = getUnitsOutstanding(calculationDate);
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
    BigDecimal navPerUnit = calculateNavPerUnit(aum, unitsOutstanding);

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
            .priceDate(calculationDate)
            .calculatedAt(Instant.now(clock))
            .securitiesDetail(buildSecuritiesDetail(calculationDate))
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
    return fundPositionRepository
        .findLatestNavDateByFundAndAsOfDate(fund, calculationDate)
        .orElse(null);
  }

  private BigDecimal getUnitsOutstanding(LocalDate calculationDate) {
    Instant cutoff = calculationDate.atTime(CUTOFF_TIME).atZone(ESTONIAN_ZONE).toInstant();
    var account = ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
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

  private BigDecimal calculateNavPerUnit(BigDecimal aum, BigDecimal unitsOutstanding) {
    if (unitsOutstanding.signum() == 0) {
      return BigDecimal.ONE;
    }
    return aum.divide(unitsOutstanding, NAV_PRECISION, HALF_UP);
  }

  private List<SecurityDetail> buildSecuritiesDetail(LocalDate priceDate) {
    return new TreeMap<>(navLedgerRepository.getSecuritiesUnitBalances())
        .entrySet().stream()
            .map(
                entry -> {
                  String isin = entry.getKey();
                  BigDecimal units = entry.getValue();
                  var resolvedPrice = positionPriceResolver.resolve(isin, priceDate);
                  String ticker = resolvedPrice.map(ResolvedPrice::storageKey).orElse("UNKNOWN");
                  BigDecimal price = resolvedPrice.map(ResolvedPrice::usedPrice).orElse(null);
                  BigDecimal marketValue =
                      price != null ? units.multiply(price).setScale(2, HALF_UP) : null;
                  return new SecurityDetail(isin, ticker, units, price, marketValue);
                })
            .toList();
  }

  private void validateResult(NavCalculationResult result) {
    if (result.navPerUnit().signum() <= 0) {
      throw new IllegalStateException(
          "NAV per unit must be positive: navPerUnit=" + result.navPerUnit());
    }
  }
}
