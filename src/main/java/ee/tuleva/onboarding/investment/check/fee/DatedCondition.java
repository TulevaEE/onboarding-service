package ee.tuleva.onboarding.investment.check.fee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record DatedCondition(LocalDate date, String description) {

  private static final int OUTSIDE_THE_EXAMINED_DAYS = -1;

  private static final int FIRST_EXAMINED_DAY = 0;

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
        identifiers.add(identify(occurrence, at));
      }
    }
    return List.copyOf(identifiers);
  }

  // A stretch reaching the oldest day the check still examines may have begun before it, so dating
  // it to that day would re-date it every morning as the lookback rolls forward - a new identifier
  // each day, and the daily alert the stretch rule exists to prevent. Only a stretch that starts
  // inside the window has a start worth naming.
  private static String identify(DatedCondition occurrence, int at) {
    return at <= FIRST_EXAMINED_DAY
        ? occurrence.description() + " ongoing"
        : occurrence.description() + " since " + occurrence.date();
  }

  private static Map<LocalDate, Integer> positionsOf(List<LocalDate> examinedDays) {
    var positions = new HashMap<LocalDate, Integer>();
    for (var day : examinedDays) {
      positions.put(day, positions.size());
    }
    return positions;
  }
}
