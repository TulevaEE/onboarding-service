package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FeeBaseCompletenessChecker {

  private static final int MAX_DAYS_IN_MESSAGE = 10;

  private final FeeAccrualRepository feeAccrualRepository;
  private final ExpectedFeeBases expectedFeeBases;
  private final PublicHolidays publicHolidays;
  private final BigDecimal feeBaseTolerance;

  FeeBaseCompletenessChecker(
      FeeAccrualRepository feeAccrualRepository,
      ExpectedFeeBases expectedFeeBases,
      PublicHolidays publicHolidays,
      @Value("${investment.fee-check.fee-base-tolerance:0.01}") BigDecimal feeBaseTolerance) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.expectedFeeBases = expectedFeeBases;
    this.publicHolidays = publicHolidays;
    this.feeBaseTolerance = feeBaseTolerance;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate from, LocalDate to) {
    var basesByDate = basesByDate(fund, from, to);

    var mismatches = new ArrayList<String>();
    var notRunDays = new ArrayList<LocalDate>();
    var totalDeviation = ZERO;
    var feeTypesSeenSoFar = EnumSet.noneOf(FeeType.class);

    for (var date : datesBetweenFirstAndLastAccrual(basesByDate)) {
      if (!publicHolidays.isWorkingDay(date)) {
        continue;
      }
      var bases = basesByDate.get(date);
      if (bases == null) {
        mismatches.add(date + " accrued no fee at all");
        continue;
      }
      totalDeviation =
          totalDeviation.add(
              checkDay(fund, date, bases, feeTypesSeenSoFar, mismatches, notRunDays));
    }

    if (!mismatches.isEmpty()) {
      return List.of(failure(fund, mismatches, totalDeviation.abs()));
    }
    if (!notRunDays.isEmpty()) {
      return List.of(notRun(fund, notRunDays));
    }
    return List.of(FeeCheckFinding.pass(fund, FEE_BASE_COMPLETENESS, ALL));
  }

  private BigDecimal checkDay(
      TulevaFund fund,
      LocalDate date,
      List<FeeBaseValue> bases,
      Set<FeeType> feeTypesSeenSoFar,
      List<String> mismatches,
      List<LocalDate> notRunDays) {
    var stopped = feeTypesThatStoppedAccruing(bases, feeTypesSeenSoFar);
    bases.forEach(base -> feeTypesSeenSoFar.add(base.feeType()));
    if (!stopped.isEmpty()) {
      mismatches.add(date + " stopped accruing " + stopped);
      return ZERO;
    }
    var expected = expectedFeeBases.expectedBases(fund, bases, date);
    if (expected.isEmpty()) {
      notRunDays.add(date);
      return ZERO;
    }
    return checkDivergence(date, bases, expected.get(), mismatches);
  }

  private BigDecimal checkDivergence(
      LocalDate date,
      List<FeeBaseValue> bases,
      Map<FeeType, BigDecimal> expected,
      List<String> mismatches) {
    var divergent = new TreeMap<String, String>();
    var dayDeviation = ZERO;
    for (var base : bases) {
      var navComponent =
          Objects.requireNonNull(
              expected.get(base.feeType()), "Expected fee base missing: feeType=" + base.feeType());
      var deviation = navComponent.subtract(base.baseValue());
      if (deviation.abs().compareTo(feeBaseTolerance) <= 0) {
        continue;
      }
      divergent.put(
          base.feeType().name(),
          "base="
              + base.baseValue().toPlainString()
              + " navComponents="
              + navComponent.toPlainString()
              + " missing="
              + deviation.toPlainString());
      dayDeviation = dayDeviation.add(deviation);
    }
    if (!divergent.isEmpty()) {
      mismatches.add(date + " " + divergent);
    }
    return dayDeviation;
  }

  private List<FeeType> feeTypesThatStoppedAccruing(
      List<FeeBaseValue> bases, Set<FeeType> seenEarlierInThisWindow) {
    var present = bases.stream().map(FeeBaseValue::feeType).collect(toSet());
    return seenEarlierInThisWindow.stream()
        .filter(feeType -> !present.contains(feeType))
        .sorted()
        .toList();
  }

  private List<LocalDate> datesBetweenFirstAndLastAccrual(
      SortedMap<LocalDate, List<FeeBaseValue>> basesByDate) {
    if (basesByDate.isEmpty()) {
      return List.of();
    }
    return basesByDate.firstKey().datesUntil(basesByDate.lastKey().plusDays(1)).collect(toList());
  }

  private SortedMap<LocalDate, List<FeeBaseValue>> basesByDate(
      TulevaFund fund, LocalDate from, LocalDate to) {
    return new TreeMap<>(
        feeAccrualRepository.findBaseValuesBetween(fund, from, to).stream()
            .collect(groupingBy(FeeBaseValue::accrualDate)));
  }

  private FeeCheckFinding failure(
      TulevaFund fund, List<String> mismatches, BigDecimal totalDeviation) {
    var shown = mismatches.stream().limit(MAX_DAYS_IN_MESSAGE).toList();
    var suffix =
        mismatches.size() > MAX_DAYS_IN_MESSAGE
            ? " ... (" + (mismatches.size() - MAX_DAYS_IN_MESSAGE) + " more)"
            : "";
    return new FeeCheckFinding(
        fund,
        FEE_BASE_COMPLETENESS,
        ALL,
        FeeCheckSeverity.FAIL,
        "Fee base does not match the published NAV components on "
            + mismatches.size()
            + " day(s): "
            + String.join(" · ", shown)
            + suffix,
        totalDeviation,
        Map.of("mismatches", mismatches, "totalDeviation", totalDeviation.toPlainString()));
  }

  private FeeCheckFinding notRun(TulevaFund fund, List<LocalDate> days) {
    return new FeeCheckFinding(
        fund,
        FEE_BASE_COMPLETENESS,
        ALL,
        FeeCheckSeverity.NOT_RUN,
        "No nav_report rows to compare the fee base against on "
            + days.size()
            + " working day(s): "
            + days.stream().limit(MAX_DAYS_IN_MESSAGE).map(LocalDate::toString).toList(),
        null,
        Map.of("daysWithoutNavReport", days.stream().map(LocalDate::toString).toList()));
  }
}
