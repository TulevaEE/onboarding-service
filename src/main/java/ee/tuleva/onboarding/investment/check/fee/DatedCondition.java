package ee.tuleva.onboarding.investment.check.fee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record DatedCondition(LocalDate date, String description) {

  private static final int OUTSIDE_THE_EXAMINED_DAYS = -1;

  private static final int FIRST_EXAMINED_DAY = 0;

  String describe() {
    return date + " " + description;
  }

  static List<String> stretchIdentifiers(
      List<DatedCondition> occurrencesInExaminedOrder, List<LocalDate> examinedDays) {
    var positions = positionsOf(examinedDays);
    var positionOfPreviousOccurrence = new HashMap<String, Integer>();
    var stretchStarts = new ArrayList<String>();
    for (var occurrence : occurrencesInExaminedOrder) {
      var at = positions.getOrDefault(occurrence.date(), OUTSIDE_THE_EXAMINED_DAYS);
      var positionOfPrevious = positionOfPreviousOccurrence.put(occurrence.description(), at);
      if (!continuesTheStretch(at, positionOfPrevious)) {
        stretchStarts.add(identify(occurrence, at));
      }
    }
    return List.copyOf(stretchStarts);
  }

  private static boolean continuesTheStretch(int at, @Nullable Integer positionOfPrevious) {
    return positionOfPrevious != null && at == positionOfPrevious + 1;
  }

  private static String identify(DatedCondition occurrence, int at) {
    return mayHaveBegunBeforeTheExaminedDays(at)
        ? occurrence.description() + " ongoing"
        : occurrence.description() + " since " + occurrence.date();
  }

  private static boolean mayHaveBegunBeforeTheExaminedDays(int position) {
    return position <= FIRST_EXAMINED_DAY;
  }

  private static Map<LocalDate, Integer> positionsOf(List<LocalDate> examinedDays) {
    var positions = new HashMap<LocalDate, Integer>();
    for (var day : examinedDays) {
      positions.put(day, positions.size());
    }
    return positions;
  }
}
