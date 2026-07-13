package ee.tuleva.onboarding.user.personalcode;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PersonalCodeTest {
  static Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC);

  @BeforeAll
  static void setup() {
    ClockHolder.setClock(clock);
  }

  @AfterAll
  static void cleanup() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void testGetAge() {
    String personalCode = "39901010015";
    assertEquals(21, PersonalCode.getAge(personalCode));
  }

  @Test
  void isMinor_trueForAChildUnderEighteen() {
    assertThat(PersonalCode.isMinor("61506150006", LocalDate.of(2026, 5, 22))).isTrue();
  }

  @Test
  void isMinor_trueOnTheDayBeforeTheEighteenthBirthday() {
    assertThat(PersonalCode.isMinor("50805230009", LocalDate.of(2026, 5, 22))).isTrue();
  }

  @Test
  void isMinor_falseFromTheEighteenthBirthday() {
    assertThat(PersonalCode.isMinor("50805220008", LocalDate.of(2026, 5, 22))).isFalse();
  }

  @Test
  void isMinor_falseForADateOfBirthInTheFuture() {
    assertThat(PersonalCode.isMinor("62701010004", LocalDate.of(2026, 5, 22))).isFalse();
  }

  @Test
  void testGetRetirementAge() {
    String personalCode = "39912310015";
    assertEquals(65, PersonalCode.getRetirementAge(personalCode));
  }

  @Test
  void testGetDateOfBirth_1900s() {
    String personalCode = "39912310015";
    LocalDate expectedDateOfBirth = LocalDate.of(1999, 12, 31);
    assertEquals(expectedDateOfBirth, PersonalCode.getDateOfBirth(personalCode));
  }

  @Test
  void testGetDateOfBirth_1800s() {
    String personalCode = "19912310015";
    LocalDate expectedDateOfBirth = LocalDate.of(1899, 12, 31);
    assertEquals(expectedDateOfBirth, PersonalCode.getDateOfBirth(personalCode));
  }

  @Test
  void testGetDateOfBirth_2000s() {
    String personalCode = "59912310015";
    LocalDate expectedDateOfBirth = LocalDate.of(2099, 12, 31);
    assertEquals(expectedDateOfBirth, PersonalCode.getDateOfBirth(personalCode));
  }

  @Test
  void testGetDateOfBirth_2100s() {
    String personalCode = "73212310015";
    LocalDate expectedDateOfBirth = LocalDate.of(2132, 12, 31);
    assertEquals(expectedDateOfBirth, PersonalCode.getDateOfBirth(personalCode));
  }

  @Test
  void testGetGenderMale() {
    assertEquals(Gender.MALE, PersonalCode.getGender("19912310015"));
    assertEquals(Gender.MALE, PersonalCode.getGender("39912310015"));
    assertEquals(Gender.MALE, PersonalCode.getGender("59912310015"));
    assertEquals(Gender.MALE, PersonalCode.getGender("79912310015"));
  }

  @Test
  void testGetGenderFemale() {
    assertEquals(Gender.FEMALE, PersonalCode.getGender("29812310015"));
    assertEquals(Gender.FEMALE, PersonalCode.getGender("49812310015"));
    assertEquals(Gender.FEMALE, PersonalCode.getGender("69812310015"));
    assertEquals(Gender.FEMALE, PersonalCode.getGender("89812310015"));
  }

  @Nested
  class RetirementDate {

    @Test
    void historicCohortsAlreadyPastRetirementUseBaseAge65() {
      assertThat(PersonalCode.getRetirementDate("35506155216")) // 1955-06-15
          .isEqualTo(LocalDate.of(2020, 6, 15));
      assertThat(PersonalCode.getRetirementDate("35808205216")) // 1958-08-20
          .isEqualTo(LocalDate.of(2023, 8, 20));
    }

    @Test
    void born1961ReachesBaseAge65() {
      assertThat(PersonalCode.getRetirementDate("36107015216")) // 1961-07-01
          .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void born1962UsesEstablishedAgeOfTheYearTheyReachIt() {
      assertThat(PersonalCode.getRetirementDate("36203105216")) // 1962-03-10, 65y1m in 2027
          .isEqualTo(LocalDate.of(2027, 4, 10));
      assertThat(PersonalCode.getRetirementDate("36212105216")) // 1962-12-10 lands in 2028, 65y3m
          .isEqualTo(LocalDate.of(2028, 3, 10));
    }

    @Test
    void born1963Reaches65Years3MonthsIn2028() {
      assertThat(PersonalCode.getRetirementDate("36305055216")) // 1963-05-05
          .isEqualTo(LocalDate.of(2028, 8, 5));
    }

    @Test
    void laterCohortsUseLatestEstablishedAge() {
      assertThat(PersonalCode.getRetirementDate("37001015216")) // 1970-01-01, 65y3m
          .isEqualTo(LocalDate.of(2035, 4, 1));
    }
  }

  @Nested
  class EarlyRetirementDate {

    @Test
    void historicCohortsAlreadyPastEarlyRetirementUseBaseAge60() {
      assertThat(PersonalCode.getEarlyRetirementDate("35808205216")) // 1958-08-20
          .isEqualTo(LocalDate.of(2018, 8, 20));
    }

    @Test
    void cohortsReaching60By2026KeepEarlyRetirementAgeAt60() {
      assertThat(PersonalCode.getEarlyRetirementDate("46503035216")) // 1965-03-03
          .isEqualTo(LocalDate.of(2025, 3, 3));
      assertThat(PersonalCode.getEarlyRetirementDate("46611305216")) // 1966-11-30
          .isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void born1967ReachesEarlyRetirementAtAgeInForceThatYear() {
      assertThat(PersonalCode.getEarlyRetirementDate("36701155216")) // 1967-01-15, 60y1m in 2027
          .isEqualTo(LocalDate.of(2027, 2, 15));
      assertThat(
              PersonalCode.getEarlyRetirementDate("36712155216")) // 1967-12-15 lands in 2028, 60y3m
          .isEqualTo(LocalDate.of(2028, 3, 15));
    }

    @Test
    void laterCohortsUseLatestEstablishedAge() {
      assertThat(PersonalCode.getEarlyRetirementDate("36806015216")) // 1968-06-01, 60y3m
          .isEqualTo(LocalDate.of(2028, 9, 1));
    }

    @Test
    void earlyRetirementUsesAgeInForceInItsOwnYearNotRetirementDateMinusFiveYears() {
      assertThat(PersonalCode.getEarlyRetirementDate("36701155216")) // 60y1m in force in 2027
          .isEqualTo(LocalDate.of(2027, 2, 15));
      assertThat(PersonalCode.getRetirementDate("36701155216")) // 65y3m in force in 2032
          .isEqualTo(LocalDate.of(2032, 4, 15));
    }
  }

  @Test
  void retirementAgeInYearsFloorsToWholeYears() {
    assertThat(PersonalCode.getRetirementAge("35808205216")).isEqualTo(65); // 65y0m
    assertThat(PersonalCode.getRetirementAge("36203105216")).isEqualTo(65); // 65y1m
    assertThat(PersonalCode.getRetirementAge("39912310015")).isEqualTo(65); // 65y3m
  }
}
