package ee.tuleva.onboarding.user.personalcode;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
  void testGetEarlyRetirementAge() {
    String personalCode = "39912310015";
    assertEquals(60, PersonalCode.getEarlyRetirementAge(personalCode));
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
}
