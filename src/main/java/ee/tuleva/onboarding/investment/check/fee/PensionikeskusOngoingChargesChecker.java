package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.PENSIONIKESKUS_ONGOING_CHARGES;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import ee.tuleva.onboarding.fund.PublishedFigure;
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
class PensionikeskusOngoingChargesChecker {

  private final OngoingChargesFigures ongoingChargesFigures;
  private final FundRepository fundRepository;
  private final PublicHolidays publicHolidays;

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    if (!isListedOnPensionikeskus(fund)) {
      return List.of();
    }
    var listed =
        requireNonNull(
            fundRepository.findByIsin(fund.getIsin()), "Fund not found: isin=" + fund.getIsin());
    var kid = ongoingChargesFigures.figureInForceOn(fund, checkDate);
    if (kid.isEmpty()) {
      return List.of(
          finding(
              fund,
              NOT_RUN,
              List.of(),
              "No KID in investment_ocf_publication in force on "
                  + checkDate
                  + " to compare Pensionikeskus' ongoing charges with"));
    }
    var shown = listed.getOngoingChargesFigure();
    if (printedAlike(shown, kid.get())
        || matchesTheKidInForceAWorkingDayAwayFrom(fund, shown, checkDate)) {
      return List.of(FeeCheckFinding.pass(fund, PENSIONIKESKUS_ONGOING_CHARGES, ALL));
    }
    return List.of(
        finding(
            fund,
            FAIL,
            List.of("pensionikeskus=" + plain(shown), "kid=" + plain(kid.get().figure())),
            "Pensionikeskus shows ongoing charges of "
                + asPrinted(shown, kid.get()).toPlainString()
                + "%, but the KID in force on "
                + checkDate
                + " prints "
                + kid.get().percent().toPlainString()
                + "%"));
  }

  private boolean matchesTheKidInForceAWorkingDayAwayFrom(
      TulevaFund fund, BigDecimal shown, LocalDate checkDate) {
    return Stream.of(
            publicHolidays.previousWorkingDay(checkDate), publicHolidays.nextWorkingDay(checkDate))
        .map(date -> ongoingChargesFigures.figureInForceOn(fund, date))
        .flatMap(Optional::stream)
        .anyMatch(kid -> printedAlike(shown, kid));
  }

  private static boolean isListedOnPensionikeskus(TulevaFund fund) {
    return fund.getPillar() != null;
  }

  private static String plain(BigDecimal figure) {
    return figure.stripTrailingZeros().toPlainString();
  }

  private static boolean printedAlike(BigDecimal shown, PublishedFigure kid) {
    return asPrinted(shown, kid).compareTo(kid.percent()) == 0;
  }

  private static BigDecimal asPrinted(BigDecimal shown, PublishedFigure kid) {
    return new PublishedFigure(shown, kid.decimals()).percent();
  }

  private static FeeCheckFinding finding(
      TulevaFund fund, FeeCheckSeverity severity, List<String> identifiers, String message) {
    return new FeeCheckFinding(
        fund, PENSIONIKESKUS_ONGOING_CHARGES, ALL, severity, message, null, identifiers, Map.of());
  }
}
