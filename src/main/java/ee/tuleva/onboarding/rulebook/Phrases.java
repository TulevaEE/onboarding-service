package ee.tuleva.onboarding.rulebook;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class Phrases {

  private static final DateTimeFormatter ESTONIAN_DATE =
      DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

  private Phrases() {}

  public static String date(final LocalDate date) {
    return ESTONIAN_DATE.format(date);
  }

  static String join(final List<String> parts, final String conjunction) {
    if (parts.size() == 1) {
      return parts.getFirst();
    }
    return String.join(", ", parts.subList(0, parts.size() - 1))
        + " "
        + conjunction
        + " "
        + parts.getLast();
  }
}
