package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  FeeBaseCompletenessChecker(
      FeeAccrualRepository feeAccrualRepository,
      FundNavQueryService fundNavQueryService,
      NavLedgerRepository navLedgerRepository,
      PublicHolidays publicHolidays,
      @Value("${investment.fee-check.fee-base-tolerance:0.01}") BigDecimal feeBaseTolerance) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.fundNavQueryService = fundNavQueryService;
    this.navLedgerRepository = navLedgerRepository;
    this.publicHolidays = publicHolidays;
    this.feeBaseTolerance = feeBaseTolerance;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate from, LocalDate to) {
    var basesByDate = basesByDate(fund, from, to);

    var mismatches = new ArrayList<String>();
    var notRunDays = new ArrayList<LocalDate>();
    var totalDeviation = ZERO;

    for (var entry : basesByDate.entrySet()) {
      var date = entry.getKey();
      if (!publicHolidays.isWorkingDay(date)) {
        continue;
      }
      var bases = entry.getValue();
      var disagreement = feeTypeDisagreement(bases);
      if (disagreement.isPresent()) {
        mismatches.add(date + " " + disagreement.get());
        continue;
      }
      var expected = expectedBase(fund, date);
      if (expected.isEmpty()) {
        notRunDays.add(date);
        continue;
      }
      var actual = bases.getFirst().baseValue();
      var deviation = expected.get().subtract(actual);
      if (deviation.abs().compareTo(feeBaseTolerance) > 0) {
        mismatches.add(
            date
                + " base="
                + actual.toPlainString()
                + " navComponents="
                + expected.get().toPlainString()
                + " missing="
                + deviation.toPlainString());
        totalDeviation = totalDeviation.add(deviation);
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

  private Optional<String> feeTypeDisagreement(List<FeeBaseValue> bases) {
    var distinct = bases.stream().map(FeeBaseValue::baseValue).map(BigDecimal::stripTrailingZeros);
    if (distinct.distinct().count() <= 1) {
      return Optional.empty();
    }
    var perType =
        bases.stream()
            .collect(
                toMap(
                    b -> b.feeType().name(),
                    b -> b.baseValue().toPlainString(),
                    (a, b) -> a,
                    TreeMap::new));
    return Optional.of("fee types disagree on the base: " + perType);
  }

  private Optional<BigDecimal> expectedBase(TulevaFund fund, LocalDate date) {
    return fundNavQueryService
        .findFeeBaseComponentTotal(fund.getCode(), date)
        .map(total -> total.add(blackrockAdjustment(fund, date)));
  }

  // NavReportMapper omits both BlackRock rows for savings funds, so their fee base carries an
  // adjustment that nav_report cannot show. Read it from the ledger instead.
  private BigDecimal blackrockAdjustment(TulevaFund fund, LocalDate date) {
    if (!fund.isSavingsFund()) {
      return ZERO;
    }
    var cutoff = date.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
    var balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            SystemAccount.BLACKROCK_ADJUSTMENT.getAccountName(fund), cutoff);
    return balance == null ? ZERO : balance;
  }

  private Map<LocalDate, List<FeeBaseValue>> basesByDate(
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
