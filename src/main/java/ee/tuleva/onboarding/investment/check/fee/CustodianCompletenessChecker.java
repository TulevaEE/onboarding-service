package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CUSTODIAN_POSITION_COMPLETENESS;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
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

  private static final int MAX_DAYS_IN_MESSAGE = 10;

  private final FundPositionRepository fundPositionRepository;
  private final CustodianPositionComparator comparator;
  private final BigDecimal materialBasisPoints;

  CustodianCompletenessChecker(
      FundPositionRepository fundPositionRepository,
      CustodianPositionComparator comparator,
      @Value("${investment.fee-check.late-correction-material-basis-points:1.00}")
          BigDecimal materialBasisPoints) {
    this.fundPositionRepository = fundPositionRepository;
    this.comparator = comparator;
    this.materialBasisPoints = materialBasisPoints;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate from, LocalDate to) {
    var navDates = fundPositionRepository.findDistinctNavDatesByFundBetween(fund, from, to);
    if (navDates.isEmpty()) {
      return notRun(fund, "No custodian position report between " + from + " and " + to);
    }

    var notComparedDates = new ArrayList<LocalDate>();
    var lateCorrections = new ArrayList<CustodianDayComparison>();
    var mismatches = new ArrayList<CustodianDayComparison>();

    for (var navDate : navDates) {
      var comparison = comparator.compare(fund, navDate);
      if (comparison.isEmpty()) {
        notComparedDates.add(navDate);
        continue;
      }
      var day = comparison.get();
      if (day.matches()) {
        continue;
      }
      if (day.needsNoCorrection(materialBasisPoints)) {
        lateCorrections.add(day);
      } else {
        mismatches.add(day);
      }
    }

    if (!mismatches.isEmpty()) {
      return List.of(mismatch(fund, mismatches));
    }
    if (!lateCorrections.isEmpty()) {
      return List.of(lateCorrection(fund, lateCorrections));
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

  private FeeCheckFinding mismatch(TulevaFund fund, List<CustodianDayComparison> days) {
    return finding(
        fund,
        FeeCheckSeverity.WARNING,
        "The SEB position report we have stored and the NAV we published disagree on "
            + days.size()
            + " day(s). Read the line(s) below in the SEB report for that date - one side is"
            + " missing what the other has. A day marked re-sent post-dates the NAV, but moves it"
            + " by more than "
            + materialBasisPoints.toPlainString()
            + " bp, so it still needs checking:\n"
            + describe(days),
        days);
  }

  private FeeCheckFinding lateCorrection(TulevaFund fund, List<CustodianDayComparison> days) {
    return finding(
        fund,
        FeeCheckSeverity.INFO,
        "SEB re-sent the position report on "
            + days.size()
            + " day(s) after we had already calculated the NAV from the earlier version, so the NAV"
            + " could not have used it. No NAV correction is due - this is what the newer report"
            + " changed, and what it would have done to that day's NAV:\n"
            + describe(days),
        days);
  }

  private String describe(List<CustodianDayComparison> days) {
    var shown =
        days.stream()
            .limit(MAX_DAYS_IN_MESSAGE)
            .map(day -> day.describeLines() + "\n" + navEffect(day) + resentTag(day))
            .toList();
    var suffix =
        days.size() > MAX_DAYS_IN_MESSAGE
            ? "\n... (" + (days.size() - MAX_DAYS_IN_MESSAGE) + " more day(s))"
            : "";
    return String.join("\n", shown) + suffix;
  }

  private String navEffect(CustodianDayComparison day) {
    return "  → effect on that day's NAV: "
        + day.navImpact().toPlainString()
        + " EUR ("
        + day.navImpactBasisPoints().toPlainString()
        + " bp)";
  }

  private String resentTag(CustodianDayComparison day) {
    return day.navPredatesReport() ? "  [SEB re-sent this date after the NAV was calculated]" : "";
  }

  private FeeCheckFinding finding(
      TulevaFund fund,
      FeeCheckSeverity severity,
      String message,
      List<CustodianDayComparison> days) {
    var totalDeviation =
        days.stream()
            .map(CustodianDayComparison::totalDifference)
            .map(BigDecimal::abs)
            .reduce(ZERO, BigDecimal::add);
    return new FeeCheckFinding(
        fund,
        CUSTODIAN_POSITION_COMPLETENESS,
        ALL,
        severity,
        message,
        totalDeviation,
        Map.of(
            "days",
            days.stream().map(day -> day.navDate().toString()).toList(),
            "lines",
            days.stream()
                .flatMap(
                    day ->
                        day.differences().stream()
                            .map(difference -> day.navDate() + " " + difference))
                .toList(),
            "totalDeviation",
            totalDeviation.toPlainString()));
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
