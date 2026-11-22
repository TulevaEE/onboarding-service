package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.KidCalendar.ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KidCalculationReader {

  private final KidFunds kidFunds;
  private final KidCalculationRepository calculations;
  private final KidPrintedFiguresRepository printedFigures;
  private final KidPublicationRepository publications;
  private final Clock clock;

  Optional<List<KidMonthCalculation>> read(
      TulevaFund fund, Optional<YearMonth> from, Optional<YearMonth> through) {
    if (!kidFunds.funds().containsKey(fund)) {
      return Optional.empty();
    }
    var first = from.orElse(ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH);
    var last = through.orElseGet(() -> KidCalendar.lastMonthEnded(KidCalendar.today(clock)));
    if (first.isAfter(last)) {
      throw new InvalidKidRequestException(
          "Range ends before it starts: from=%s, through=%s".formatted(first, last));
    }
    var latest = calculations.findLatestVersions(fund, first, last);
    var ids = latest.stream().map(StoredKidCalculation::id).toList();
    var scenarios = printedFigures.findPrintedScenarios(ids);
    var costs = printedFigures.findPrintedCosts(ids);
    var published = publications.findPublications(fund, first, last);
    return Optional.of(
        latest.stream()
            .map(
                calculation ->
                    new KidMonthCalculation(
                        calculation.month(),
                        calculation.id(),
                        calculation.version(),
                        calculation.calculatedAt(),
                        calculation.refusalReasons(),
                        calculation.ocf(),
                        scenarios.getOrDefault(calculation.id(), List.of()),
                        costs.getOrDefault(calculation.id(), List.of()),
                        published.get(calculation.month())))
            .toList());
  }
}
