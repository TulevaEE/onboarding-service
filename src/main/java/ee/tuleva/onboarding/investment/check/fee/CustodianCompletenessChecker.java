package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CUSTODIAN_POSITION_COMPLETENESS;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The fee base recomputes cleanly from the NAV components even when a custodian row never made it
// into the ledger, because those components are themselves fed by the filtered ingestion. Only
// comparing against the custodian report can see a wrong input rather than a wrong formula.
@Component
class CustodianCompletenessChecker {

  private static final List<AccountType> CUSTODIAN_TYPES = List.of(CASH, RECEIVABLES, LIABILITY);
  private static final int MAX_DAYS_IN_MESSAGE = 10;

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
    var navDates = fundPositionRepository.findDistinctNavDatesByFundBetween(fund, from, to);
    if (navDates.isEmpty()) {
      return notRun(fund, "No custodian position report between " + from + " and " + to);
    }

    var mismatches = new ArrayList<String>();
    var notComparedDates = new ArrayList<LocalDate>();
    var totalDeviation = ZERO;

    for (var navDate : navDates) {
      var recognised = fundNavQueryService.findCustodianComparableTotal(fund.getCode(), navDate);
      if (recognised.isEmpty()) {
        notComparedDates.add(navDate);
        continue;
      }
      var reported =
          fundPositionRepository.sumCustodianMarketValue(
              fund, navDate, CUSTODIAN_TYPES, fund.getIsin());
      var deviation = reported.subtract(recognised.get());
      if (deviation.abs().compareTo(custodianTolerance) > 0) {
        mismatches.add(describe(navDate, reported, recognised.get(), deviation));
        totalDeviation = totalDeviation.add(deviation);
      }
    }

    if (!mismatches.isEmpty()) {
      return List.of(unrecognised(fund, mismatches, totalDeviation.abs()));
    }
    if (!notComparedDates.isEmpty()) {
      return notRun(
          fund,
          "No nav_report rows to compare the custodian positions against on "
              + notComparedDates.size()
              + " position date(s): "
              + notComparedDates.stream()
                  .limit(MAX_DAYS_IN_MESSAGE)
                  .map(LocalDate::toString)
                  .toList());
    }
    return List.of(FeeCheckFinding.pass(fund, CUSTODIAN_POSITION_COMPLETENESS, ALL));
  }

  private String describe(
      LocalDate navDate, BigDecimal reported, BigDecimal recognised, BigDecimal deviation) {
    return navDate
        + " custodian="
        + reported.toPlainString()
        + " navRecognised="
        + recognised.toPlainString()
        + " unrecognised="
        + deviation.toPlainString();
  }

  private FeeCheckFinding unrecognised(
      TulevaFund fund, List<String> mismatches, BigDecimal totalDeviation) {
    var shown = mismatches.stream().limit(MAX_DAYS_IN_MESSAGE).toList();
    var suffix =
        mismatches.size() > MAX_DAYS_IN_MESSAGE
            ? " ... (" + (mismatches.size() - MAX_DAYS_IN_MESSAGE) + " more)"
            : "";
    return new FeeCheckFinding(
        fund,
        CUSTODIAN_POSITION_COMPLETENESS,
        ALL,
        FeeCheckSeverity.WARNING,
        "Custodian positions do not match what the NAV recognised on "
            + mismatches.size()
            + " day(s): "
            + String.join(" · ", shown)
            + suffix,
        totalDeviation,
        Map.of("mismatches", mismatches, "totalDeviation", totalDeviation.toPlainString()));
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
