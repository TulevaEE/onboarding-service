package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FeeBaseCompletenessChecker {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final int MAX_DAYS_IN_MESSAGE = 10;

  private final FeeAccrualRepository feeAccrualRepository;
  private final FundNavQueryService fundNavQueryService;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;
  private final BigDecimal feeBaseTolerance;
  private final LocalDate depotAssetBaseFrom;

  FeeBaseCompletenessChecker(
      FeeAccrualRepository feeAccrualRepository,
      FundNavQueryService fundNavQueryService,
      NavLedgerRepository navLedgerRepository,
      PublicHolidays publicHolidays,
      @Value("${investment.fee-check.fee-base-tolerance:0.01}") BigDecimal feeBaseTolerance,
      @Value("${investment.fee-check.depot-asset-base-from:2026-08-15}")
          LocalDate depotAssetBaseFrom) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.fundNavQueryService = fundNavQueryService;
    this.navLedgerRepository = navLedgerRepository;
    this.publicHolidays = publicHolidays;
    this.feeBaseTolerance = feeBaseTolerance;
    this.depotAssetBaseFrom = depotAssetBaseFrom;
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
      var stopped = feeTypesThatStoppedAccruing(bases, feeTypesSeenSoFar);
      bases.forEach(base -> feeTypesSeenSoFar.add(base.feeType()));
      if (!stopped.isEmpty()) {
        mismatches.add(date + " stopped accruing " + stopped);
        continue;
      }
      var expected = expectedBases(fund, bases, date);
      if (expected.isEmpty()) {
        notRunDays.add(date);
        continue;
      }
      var divergent = new TreeMap<String, String>();
      for (var base : bases) {
        var navComponent =
            Objects.requireNonNull(
                expected.get().get(base.feeType()),
                "Expected fee base missing: feeType=" + base.feeType());
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
        totalDeviation = totalDeviation.add(deviation);
      }
      if (!divergent.isEmpty()) {
        mismatches.add(date + " " + divergent);
      }
    }

    if (!mismatches.isEmpty()) {
      return List.of(failure(fund, mismatches, totalDeviation.abs()));
    }
    if (!notRunDays.isEmpty()) {
      return List.of(notRun(fund, notRunDays));
    }
    return List.of(FeeCheckFinding.pass(fund, FEE_BASE_COMPLETENESS, ALL));
  }

  private List<FeeType> feeTypesThatStoppedAccruing(
      List<FeeBaseValue> bases, Set<FeeType> seenEarlierInThisWindow) {
    var present = bases.stream().map(FeeBaseValue::feeType).collect(toSet());
    return seenEarlierInThisWindow.stream()
        .filter(feeType -> !present.contains(feeType))
        .sorted()
        .toList();
  }

  private Optional<Map<FeeType, BigDecimal>> expectedBases(
      TulevaFund fund, List<FeeBaseValue> bases, LocalDate date) {
    var expected = new EnumMap<FeeType, BigDecimal>(FeeType.class);
    for (var base : bases) {
      var value = expectedBase(fund, base.feeType(), date);
      if (value.isEmpty()) {
        return Optional.empty();
      }
      expected.put(base.feeType(), value.get());
    }
    return Optional.of(expected);
  }

  private Optional<BigDecimal> expectedBase(TulevaFund fund, FeeType feeType, LocalDate date) {
    return chargesDepotOnAssetValue(feeType, date)
        ? fundNavQueryService
            .findAssetTotal(fund.getCode(), date)
            .map(total -> total.add(assetSideBlackrockAdjustment(fund, date)))
        : fundNavQueryService
            .findFeeBaseComponentTotal(fund.getCode(), date)
            .map(total -> total.add(navFeeBaseBlackrockAdjustment(fund, date)));
  }

  private boolean chargesDepotOnAssetValue(FeeType feeType, LocalDate accrualDate) {
    return feeType == FeeType.DEPOT && !accrualDate.isBefore(depotAssetBaseFrom);
  }

  private BigDecimal navFeeBaseBlackrockAdjustment(TulevaFund fund, LocalDate positionReportDate) {
    return fund.isSavingsFund()
        ? blackrockAdjustmentMissingFromNavReport(fund, positionReportDate)
        : ZERO;
  }

  private BigDecimal assetSideBlackrockAdjustment(TulevaFund fund, LocalDate positionReportDate) {
    var adjustment = blackrockAdjustmentMissingFromNavReport(fund, positionReportDate);
    return fund.isSavingsFund() ? adjustment : adjustment.min(ZERO);
  }

  private BigDecimal blackrockAdjustmentMissingFromNavReport(
      TulevaFund fund, LocalDate positionReportDate) {
    var balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            SystemAccount.BLACKROCK_ADJUSTMENT.getAccountName(fund),
            navCutoffThatChargedTheFee(fund, positionReportDate));
    return balance == null ? ZERO : balance;
  }

  private Instant navCutoffThatChargedTheFee(TulevaFund fund, LocalDate positionReportDate) {
    return publicHolidays
        .nextWorkingDay(positionReportDate)
        .atTime(fund.getNavCutoffTime())
        .atZone(ESTONIAN_ZONE)
        .toInstant();
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
