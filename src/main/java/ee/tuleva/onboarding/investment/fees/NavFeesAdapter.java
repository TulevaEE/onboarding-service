package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeBases;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeResult;
import ee.tuleva.onboarding.savings.fund.nav.NavFees;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class NavFeesAdapter implements NavFees {

  private final FeeCalculationService feeCalculationService;

  NavFeesAdapter(FeeCalculationService feeCalculationService) {
    this.feeCalculationService = feeCalculationService;
  }

  @Override
  public NavFeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      NavFeeBases bases,
      Instant feeCutoff,
      Map<String, ResolvedPrice> securityPrices) {
    FeeResult result =
        feeCalculationService.calculateFeesForNav(
            fund,
            positionReportDate,
            new FeeBases(bases.navFeeBase(), bases.assetValue()),
            feeCutoff,
            securityPrices);
    return new NavFeeResult(result.managementFeeAccrual(), result.depotFeeAccrual());
  }
}
