package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.components.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavCalculationService {

  private static final int NAV_PRECISION = 5;

  private final FundPositionRepository fundPositionRepository;
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

    BigDecimal unitsOutstanding = getUnitsOutstanding();
    context.setUnitsOutstanding(unitsOutstanding);

    BigDecimal preliminaryNav =
        calculatePreliminaryNav(
            securitiesValue,
            cashPosition,
            receivables,
            payables,
            pendingSubscriptions,
            managementFeeAccrual,
            depotFeeAccrual,
            blackrockAdjustment);

    BigDecimal preliminaryNavPerUnit = calculateNavPerUnit(preliminaryNav, unitsOutstanding);
    context.setPreliminaryNav(preliminaryNav);
    context.setPreliminaryNavPerUnit(preliminaryNavPerUnit);

    BigDecimal pendingRedemptions = ZERO;
    if (unitsOutstanding.signum() > 0) {
      pendingRedemptions = redemptionsComponent.calculate(context);
    }

    BigDecimal aum = preliminaryNav.subtract(pendingRedemptions);
    BigDecimal navPerUnit = calculateNavPerUnit(aum, unitsOutstanding);

    Map<String, Object> componentDetails = buildComponentDetails(context);

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
            .componentDetails(componentDetails)
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
        .findLatestReportingDateByFundAndAsOfDate(fund, calculationDate)
        .orElse(null);
  }

  private BigDecimal getUnitsOutstanding() {
    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalance(FUND_UNITS_OUTSTANDING.getAccountName());
    if (balance.signum() < 0) {
      throw new IllegalStateException(
          "FUND_UNITS_OUTSTANDING should be positive, but was: " + balance);
    }
    return balance;
  }

  private BigDecimal calculatePreliminaryNav(
      BigDecimal securitiesValue,
      BigDecimal cashPosition,
      BigDecimal receivables,
      BigDecimal payables,
      BigDecimal pendingSubscriptions,
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

  private void validateResult(NavCalculationResult result) {
    if (result.navPerUnit().signum() <= 0) {
      throw new IllegalStateException(
          "NAV per unit must be positive: navPerUnit=" + result.navPerUnit());
    }
  }

  // TODO: use immutable Map.of()
  private Map<String, Object> buildComponentDetails(NavComponentContext context) {
    Map<String, Object> details = new HashMap<>();
    details.put("positionReportDate", context.getPositionReportDate());
    details.put("priceDate", context.getPriceDate());
    details.put("preliminaryNav", context.getPreliminaryNav());
    details.put("preliminaryNavPerUnit", context.getPreliminaryNavPerUnit());
    return details;
  }
}
