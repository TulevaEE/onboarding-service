package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.KID_AFTER_FEE_CHANGE;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.ocf.KidsInForce;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KidAfterFeeChangeChecker {

  private static final int WORKING_DAYS_OF_WARNING_BEFORE_A_FEE_CHANGE = 10;

  private final FeeRateRepository feeRateRepository;
  private final KidsInForce kidsInForce;
  private final PublicHolidays publicHolidays;

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    var inForce = feeRateRepository.findValidRate(fund, FeeType.MANAGEMENT, checkDate);
    if (inForce.isEmpty()) {
      return List.of(noFeeRateInForce(fund, checkDate));
    }
    var missingKids =
        Stream.of(
                missingKid(fund, inForce.get(), warningStart(inForce.get()), checkDate, FAIL),
                feeRateRepository
                    .findNextRateAfter(fund, FeeType.MANAGEMENT, checkDate)
                    .flatMap(change -> missingKidOnceWarned(fund, change, checkDate)))
            .flatMap(Optional::stream)
            .toList();
    return missingKids.isEmpty()
        ? List.of(FeeCheckFinding.pass(fund, KID_AFTER_FEE_CHANGE, MANAGEMENT))
        : missingKids;
  }

  private Optional<FeeCheckFinding> missingKidOnceWarned(
      TulevaFund fund, FeeRate change, LocalDate checkDate) {
    var countsFrom = warningStart(change);
    return checkDate.isBefore(countsFrom)
        ? Optional.empty()
        : missingKid(fund, change, countsFrom, change.validFrom(), WARNING);
  }

  private Optional<FeeCheckFinding> missingKid(
      TulevaFund fund,
      FeeRate change,
      LocalDate countsFrom,
      LocalDate kidDate,
      FeeCheckSeverity severity) {
    var kidInForce = kidsInForce.validFromOfTheOneInForceOn(fund, kidDate);
    if (kidInForce.filter(validFrom -> !validFrom.isBefore(countsFrom)).isPresent()) {
      return Optional.empty();
    }
    var kidOnThatDate =
        kidInForce
            .map(validFrom -> "the KID in force on %s is from %s".formatted(kidDate, validFrom))
            .orElseGet(
                () -> "no KID in force on %s in investment_ocf_publication".formatted(kidDate));
    return Optional.of(
        finding(
            fund,
            severity,
            change,
            "Management fee %s from %s has no KID taking effect with it (%s; one from %s or later"
                    .formatted(
                        percent(change.annualRate()), change.validFrom(), kidOnThatDate, countsFrom)
                + " counts). Record the KID for this fee with ocf.publish_given"));
  }

  private LocalDate warningStart(FeeRate change) {
    return Stream.iterate(change.validFrom(), publicHolidays::previousWorkingDay)
        .skip(WORKING_DAYS_OF_WARNING_BEFORE_A_FEE_CHANGE)
        .findFirst()
        .orElseThrow();
  }

  private static String percent(BigDecimal annualRate) {
    return annualRate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
  }

  private static FeeCheckFinding finding(
      TulevaFund fund, FeeCheckSeverity severity, FeeRate change, String message) {
    return new FeeCheckFinding(
        fund,
        KID_AFTER_FEE_CHANGE,
        MANAGEMENT,
        severity,
        message,
        null,
        List.of("feeFrom=" + change.validFrom()),
        Map.of());
  }

  private static FeeCheckFinding noFeeRateInForce(TulevaFund fund, LocalDate checkDate) {
    return new FeeCheckFinding(
        fund,
        KID_AFTER_FEE_CHANGE,
        MANAGEMENT,
        NOT_RUN,
        "No management fee rate in investment_fee_rate in force on " + checkDate,
        null,
        List.of(),
        Map.of());
  }
}
