package ee.tuleva.onboarding.user.personalcode;

import static ee.tuleva.onboarding.user.personalcode.Gender.*;
import static ee.tuleva.onboarding.user.personalcode.Gender.FEMALE;
import static java.time.format.ResolverStyle.STRICT;
import static java.time.temporal.ChronoField.YEAR;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class PersonalCode {

  public static int getAge(String personalCode) {
    LocalDate today = LocalDate.now();
    LocalDate dateOfBirth = getDateOfBirth(personalCode);
    Period period = Period.between(dateOfBirth, today);

    return period.getYears();
  }

  public static int getEarlyRetirementAge(String personalCode) {
    // TODO: make more accurate in the future
    return 60;
  }

  public static int getRetirementAge(String personalCode) {
    // TODO: make more accurate in the future
    return 65;
  }

  public static LocalDate getDateOfBirth(String personalCode) {
    String century = personalCode.substring(0, 1);
    String dateOfBirth = personalCode.substring(1, 7);
    return LocalDate.parse(dateOfBirth, dateOfBirthFormatter(century));
  }

  public static Gender getGender(String personalCode) {
    int genderNumber = Integer.parseInt(personalCode.substring(0, 1));
    return genderNumber % 2 == 0 ? FEMALE : MALE;
  }

  private static DateTimeFormatter dateOfBirthFormatter(String century) {
    if (isBornInThe20thCentury(century)) {
      return formatterWithBaseYear(1900);
    }
    return DateTimeFormatter.ofPattern("uuMMdd").withResolverStyle(STRICT);
  }

  private static boolean isBornInThe20thCentury(String centuryIndicator) {
    return "3".equals(centuryIndicator) || "4".equals(centuryIndicator);
  }

  private static DateTimeFormatter formatterWithBaseYear(int baseYear) {
    return new DateTimeFormatterBuilder()
        .appendValueReduced(YEAR, 2, 2, baseYear)
        .appendPattern("MMdd")
        .toFormatter()
        .withResolverStyle(STRICT);
  }
}
