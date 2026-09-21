package ee.tuleva.onboarding.investment.check.fee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record DatedCondition(LocalDate date, String description) {

  private static final int OUTSIDE_THE_EXAMINED_DAYS = -1;

  String describe() {
    return date + " " + description;
  }

  // A condition that has not been fixed restates itself on every later day the check looks at, so
  // one identifier per day would make the notifier announce the same standing condition every
  // morning. Naming the stretch it began on lets it speak when it starts, and again when a later
  // stretch of the same condition begins, but not once per additional day it is still true.
  // Occurrences arrive in the order the check examined the days.
  static List<String> stretchIdentifiers(
      List<DatedCondition> occurrences, List<LocalDate> examinedDays) {
    var position = positionsOf(examinedDays);
    var positionOfPreviousOccurrence = new HashMap<String, Integer>();
    var identifiers = new ArrayList<String>();
    for (var occurrence : occurrences) {
      var at = position.getOrDefault(occurrence.date(), OUTSIDE_THE_EXAMINED_DAYS);
      var previous = positionOfPreviousOccurrence.put(occurrence.description(), at);
      if (previous == null || at != previous + 1) {
        identifiers.add(occurrence.description() + " since " + occurrence.date());
      }
    }
    return List.copyOf(identifiers);
  }

  private static Map<LocalDate, Integer> positionsOf(List<LocalDate> examinedDays) {
    var positions = new HashMap<LocalDate, Integer>();
    for (var day : examinedDays) {
      positions.put(day, positions.size());
    }
    return positions;
  }
}
