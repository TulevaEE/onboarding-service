package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.PENSIONIKESKUS_MANAGEMENT_FEE;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
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
class PensionikeskusManagementFeeChecker {

  private final FeeRateRepository feeRateRepository;
  private final FundRepository fundRepository;
  private final PublicHolidays publicHolidays;

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    if (!isListedOnPensionikeskus(fund)) {
      return List.of();
    }
    var listed =
        requireNonNull(
            fundRepository.findByIsin(fund.getIsin()), "Fund not found: isin=" + fund.getIsin());
    var inForce = rateInForceOn(fund, checkDate);
    if (inForce.isEmpty()) {
      return List.of(
          finding(
              fund,
              NOT_RUN,
              List.of(),
              "No management fee rate in investment_fee_rate on "
                  + checkDate
                  + " to compare Pensionikeskus' with"));
    }
    var shown = listed.getManagementFeeRate();
    if (shown.compareTo(inForce.get()) == 0
        || matchesTheRateInForceAWorkingDayAwayFrom(fund, shown, checkDate)) {
      return List.of(FeeCheckFinding.pass(fund, PENSIONIKESKUS_MANAGEMENT_FEE, MANAGEMENT));
    }
    return List.of(
        finding(
            fund,
            FAIL,
            List.of("pensionikeskus=" + plain(shown), "inForce=" + plain(inForce.get())),
            "Pensionikeskus shows a management fee of "
                + plain(shown)
                + ", but investment_fee_rate has "
                + plain(inForce.get())
                + " in force on "
                + checkDate));
  }

  private boolean matchesTheRateInForceAWorkingDayAwayFrom(
      TulevaFund fund, BigDecimal shown, LocalDate checkDate) {
    return Stream.of(
            publicHolidays.previousWorkingDay(checkDate), publicHolidays.nextWorkingDay(checkDate))
        .map(date -> rateInForceOn(fund, date))
        .flatMap(Optional::stream)
        .anyMatch(rate -> rate.compareTo(shown) == 0);
  }

  private Optional<BigDecimal> rateInForceOn(TulevaFund fund, LocalDate date) {
    return feeRateRepository.findValidRate(fund, FeeType.MANAGEMENT, date).map(FeeRate::annualRate);
  }

  private static boolean isListedOnPensionikeskus(TulevaFund fund) {
    return fund.getPillar() != null;
  }

  private static String plain(BigDecimal rate) {
    return rate.stripTrailingZeros().toPlainString();
  }

  private static FeeCheckFinding finding(
      TulevaFund fund, FeeCheckSeverity severity, List<String> identifiers, String message) {
    return new FeeCheckFinding(
        fund,
        PENSIONIKESKUS_MANAGEMENT_FEE,
        MANAGEMENT,
        severity,
        message,
        null,
        identifiers,
        Map.of());
  }
}
