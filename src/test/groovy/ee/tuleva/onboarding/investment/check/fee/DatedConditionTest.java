package ee.tuleva.onboarding.investment.check.fee;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatedConditionTest {

  private static final String CONDITION = "no nav_report";

  private static final String OTHER_CONDITION = "stopped accruing MANAGEMENT";

  @Test
  void aStretchThatStartsInsideTheWindowIsNamedByTheDayItStarted() {
    var examinedDays = days(LocalDate.of(2026, 5, 1), 5);
    var occurrences = occurrencesOn(examinedDays.subList(2, 5), CONDITION);

    assertThat(DatedCondition.stretchIdentifiers(occurrences, examinedDays))
        .containsExactly(CONDITION + " since 2026-05-03");
  }

  @Test
  void aStretchReachingTheOldestExaminedDayKeepsOneIdentifierAsTheWindowRollsForward() {
    var firstWindow = days(LocalDate.of(2026, 5, 1), 5);
    var rolledWindow = days(LocalDate.of(2026, 5, 2), 5);

    var onFirstWindow =
        DatedCondition.stretchIdentifiers(occurrencesOn(firstWindow, CONDITION), firstWindow);
    var onRolledWindow =
        DatedCondition.stretchIdentifiers(occurrencesOn(rolledWindow, CONDITION), rolledWindow);

    assertThat(onFirstWindow).containsExactly(CONDITION + " ongoing").isEqualTo(onRolledWindow);
  }

  @Test
  void aSecondStretchOfTheSameConditionIsNamedSeparately() {
    var examinedDays = days(LocalDate.of(2026, 5, 1), 6);
    var occurrences =
        concat(
            occurrencesOn(examinedDays.subList(0, 2), CONDITION),
            occurrencesOn(examinedDays.subList(4, 6), CONDITION));

    assertThat(DatedCondition.stretchIdentifiers(occurrences, examinedDays))
        .containsExactly(CONDITION + " ongoing", CONDITION + " since 2026-05-05");
  }

  @Test
  void twoConditionsRunningTogetherKeepTheirOwnStretches() {
    var examinedDays = days(LocalDate.of(2026, 5, 1), 3);
    var occurrences =
        concat(
            occurrencesOn(examinedDays, CONDITION),
            occurrencesOn(examinedDays.subList(1, 3), OTHER_CONDITION));

    assertThat(DatedCondition.stretchIdentifiers(occurrences, examinedDays))
        .containsExactly(CONDITION + " ongoing", OTHER_CONDITION + " since 2026-05-02");
  }

  private static List<LocalDate> days(LocalDate from, int count) {
    return java.util.stream.IntStream.range(0, count).mapToObj(from::plusDays).toList();
  }

  private static List<DatedCondition> occurrencesOn(List<LocalDate> dates, String description) {
    return dates.stream().map(date -> new DatedCondition(date, description)).toList();
  }

  private static List<DatedCondition> concat(
      List<DatedCondition> first, List<DatedCondition> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
  }
}
