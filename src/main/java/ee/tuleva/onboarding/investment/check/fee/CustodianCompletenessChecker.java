package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CUSTODIAN_POSITION_COMPLETENESS;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The fee base recomputes cleanly from the NAV components even when a custodian row never made it
// into the ledger, because those components are themselves fed by the filtered ingestion. This
// compares the custodian report against what the NAV recognised, so a wrong input is visible where
// a wrong formula is not.
@Component
class CustodianCompletenessChecker {

  private static final List<AccountType> CUSTODIAN_TYPES = List.of(CASH, RECEIVABLES, LIABILITY);

  private final FundPositionRepository fundPositionRepository;
  private final FundNavQueryService fundNavQueryService;
  private final BigDecimal custodianTolerance;

  CustodianCompletenessChecker(
      FundPositionRepository fundPositionRepository,
      FundNavQueryService fundNavQueryService,
      @Value("${investment.fee-check.custodian-tolerance:1.00}") BigDecimal custodianTolerance) {
    this.fundPositionRepository = fundPositionRepository;
    this.fundNavQueryService = fundNavQueryService;
    this.custodianTolerance = custodianTolerance;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate from, LocalDate to) {
    var navDate =
        fundPositionRepository
            .findLatestNavDateByFundAndAsOfDate(fund, to)
            .filter(date -> !date.isBefore(from));
    if (navDate.isEmpty()) {
      return notRun(fund, "No custodian position report between " + from + " and " + to);
    }

    var recognised =
        fundNavQueryService.findCustodianComparableTotal(fund.getCode(), navDate.get());
    if (recognised.isEmpty()) {
      return notRun(fund, "No nav_report rows for position date " + navDate.get());
    }

    var reported =
        fundPositionRepository.sumCustodianMarketValue(
            fund, navDate.get(), CUSTODIAN_TYPES, fund.getIsin());
    var deviation = reported.subtract(recognised.get());
    if (deviation.abs().compareTo(custodianTolerance) <= 0) {
      return List.of(FeeCheckFinding.pass(fund, CUSTODIAN_POSITION_COMPLETENESS, ALL));
    }
    return List.of(unrecognised(fund, navDate.get(), reported, recognised.get(), deviation));
  }

  private FeeCheckFinding unrecognised(
      TulevaFund fund,
      LocalDate navDate,
      BigDecimal reported,
      BigDecimal recognised,
      BigDecimal deviation) {
    return new FeeCheckFinding(
        fund,
        CUSTODIAN_POSITION_COMPLETENESS,
        ALL,
        FeeCheckSeverity.WARNING,
        "Custodian positions on "
            + navDate
            + " do not match what the NAV recognised: custodian="
            + reported.toPlainString()
            + " navRecognised="
            + recognised.toPlainString()
            + " unrecognised="
            + deviation.toPlainString(),
        deviation.abs(),
        Map.of(
            "navDate",
            navDate.toString(),
            "custodianReported",
            reported.toPlainString(),
            "navRecognised",
            recognised.toPlainString(),
            "unrecognised",
            deviation.toPlainString()));
  }

  private List<FeeCheckFinding> notRun(TulevaFund fund, String message) {
    return List.of(
        new FeeCheckFinding(
            fund,
            CUSTODIAN_POSITION_COMPLETENESS,
            ALL,
            FeeCheckSeverity.NOT_RUN,
            message,
            null,
            Map.of()));
  }
}
