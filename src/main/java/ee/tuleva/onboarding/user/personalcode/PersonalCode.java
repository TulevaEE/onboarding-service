package ee.tuleva.onboarding.user.personalcode;

import static ee.tuleva.onboarding.time.ClockHolder.clock;
import static ee.tuleva.onboarding.user.personalcode.Gender.*;
import static ee.tuleva.onboarding.user.personalcode.Gender.FEMALE;
import static java.time.format.ResolverStyle.STRICT;
import static java.time.temporal.ChronoField.YEAR;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PersonalCode {

  private static final NavigableMap<Integer, Period> ESTABLISHED_RETIREMENT_AGE_BY_YEAR =
      new TreeMap<>(
          Map.of(
              2027, Period.of(65, 1, 0),
              2028, Period.of(65, 3, 0)));

  private static final Period BASE_RETIREMENT_AGE = Period.ofYears(65);

  public static int getAge(String personalCode) {
    LocalDate today = clock().instant().atZone(ZoneId.systemDefault()).toLocalDate();
    LocalDate dateOfBirth = getDateOfBirth(personalCode);
    Period period = Period.between(dateOfBirth, today);

    return period.getYears();
  }

  public static int getRetirementAge(String personalCode) {
    return Period.between(getDateOfBirth(personalCode), getRetirementDate(personalCode)).getYears();
  }

  public static LocalDate getRetirementDate(String personalCode) {
    return retirementDate(personalCode, 0);
  }

  public static LocalDate getEarlyRetirementDate(String personalCode) {
    return retirementDate(personalCode, 5);
  }

  private static LocalDate retirementDate(String personalCode, int yearsEarly) {
    LocalDate dateOfBirth = getDateOfBirth(personalCode);
    int firstCandidateYear = dateOfBirth.plus(BASE_RETIREMENT_AGE).minusYears(yearsEarly).getYear();
    for (int year = firstCandidateYear; year <= firstCandidateYear + 10; year++) {
      LocalDate date = dateOfBirth.plus(retirementAgeInForce(year)).minusYears(yearsEarly);
      if (date.getYear() == year) {
        return date;
      }
    }
    throw new IllegalStateException(
        "Could not resolve retirement date: dateOfBirth=" + dateOfBirth);
  }

  public static int lastEstablishedRetirementAgeYear() {
    return ESTABLISHED_RETIREMENT_AGE_BY_YEAR.lastKey();
  }

  private static Period retirementAgeInForce(int year) {
    var established = ESTABLISHED_RETIREMENT_AGE_BY_YEAR.floorEntry(year);
    return established == null ? BASE_RETIREMENT_AGE : established.getValue();
  }

  public static LocalDate getDateOfBirth(String personalCode) {
    String dateOfBirth = personalCode.substring(1, 7);
    return LocalDate.parse(dateOfBirth, dateOfBirthFormatter(personalCode));
  }

  public static Gender getGender(String personalCode) {
    return getCenturyBaseYearAndGender(personalCode).gender;
  }

  private static DateTimeFormatter dateOfBirthFormatter(String idCode) {
    return formatterWithBaseYear(getCenturyBaseYearAndGender(idCode).centuryBaseYear);
  }

  private static BaseCenturyYearAndGender getCenturyBaseYearAndGender(String idCode) {
    char firstDigit = idCode.charAt(0);

    return switch (firstDigit) {
      case '1' -> new BaseCenturyYearAndGender(MALE, 1800);
      case '2' -> new BaseCenturyYearAndGender(FEMALE, 1800);
      case '3' -> new BaseCenturyYearAndGender(MALE, 1900);
      case '4' -> new BaseCenturyYearAndGender(FEMALE, 1900);
      case '5' -> new BaseCenturyYearAndGender(MALE, 2000);
      case '6' -> new BaseCenturyYearAndGender(FEMALE, 2000);
      case '7' -> new BaseCenturyYearAndGender(MALE, 2100);
      case '8' -> new BaseCenturyYearAndGender(FEMALE, 2100);
      default -> throw new IllegalArgumentException("Invalid first digit");
    };
  }

  private static DateTimeFormatter formatterWithBaseYear(int baseYear) {
    return new DateTimeFormatterBuilder()
        .appendValueReduced(YEAR, 2, 2, baseYear)
        .appendPattern("MMdd")
        .toFormatter()
        .withResolverStyle(STRICT);
  }

  private record BaseCenturyYearAndGender(Gender gender, int centuryBaseYear) {}
}
