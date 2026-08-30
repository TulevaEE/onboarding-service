package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeBases;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeResult;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeType;
import ee.tuleva.onboarding.savings.fund.nav.NavFees;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class NavFeesAdapter implements NavFees {

  private final Delegates delegates;

  NavFeesAdapter(
      FeeCalculationService feeCalculationService, FeeChargedToFundPolicy feeChargedToFundPolicy) {
    this.delegates = new Delegates(feeCalculationService, feeChargedToFundPolicy);
  }

  @Override
  public NavFeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      NavFeeBases bases,
      Instant feeCutoff,
      Map<String, ResolvedPrice> securityPrices) {
    FeeResult result =
        delegates
            .feeCalculationService()
            .calculateFeesForNav(
                fund,
                positionReportDate,
                new FeeBases(bases.navFeeBase(), bases.assetValue()),
                feeCutoff,
                securityPrices);
    return new NavFeeResult(result.managementFeeAccrual(), result.depotFeeAccrual());
  }

  @Override
  public boolean chargedToFund(TulevaFund fund, NavFeeType feeType, LocalDate date) {
    return delegates
        .feeChargedToFundPolicy()
        .chargedToFund(
            fund,
            switch (feeType) {
              case MANAGEMENT -> FeeType.MANAGEMENT;
              case DEPOT -> FeeType.DEPOT;
            },
            date);
  }

  private record Delegates(
      FeeCalculationService feeCalculationService, FeeChargedToFundPolicy feeChargedToFundPolicy) {}
}
