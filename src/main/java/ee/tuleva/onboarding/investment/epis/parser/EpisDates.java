package ee.tuleva.onboarding.investment.epis.parser;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class EpisDates {

  private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

  private EpisDates() {}

  public static @Nullable LocalDate findDate(String line) {
    Matcher matcher = DATE.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    return LocalDate.of(
        Integer.parseInt(matcher.group(3)),
        Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(1)));
  }
}
