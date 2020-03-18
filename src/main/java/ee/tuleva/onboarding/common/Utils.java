package ee.tuleva.onboarding.common;

import java.security.InvalidParameterException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {

  public static Instant parseInstant(String inputDate) {
    try {
      return new SimpleDateFormat("yyyy-MM-dd").parse(inputDate).toInstant();
    } catch (ParseException e) {
      log.error("Error parsing date from " + inputDate, e);
      throw new InvalidParameterException("Error parsing date from: " + inputDate);
    }
  }
}
