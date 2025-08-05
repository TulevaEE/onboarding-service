package ee.tuleva.onboarding.deadline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull; // Added for basic check

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MandateDeadlinesServiceTest {

  private final Instant fixedTime = Instant.parse("2021-03-11T10:00:00Z");
  private Clock clock;
  private PublicHolidays publicHolidays;
  private MandateDeadlinesService service;

  @BeforeEach
  void setup() {
    clock = Clock.fixed(fixedTime, ZoneOffset.UTC);
    publicHolidays = new PublicHolidays();
    service = new MandateDeadlinesService(clock, publicHolidays);
  }

  @Test
  void canGetMandateDeadlines_usesCurrentTimeFromClock() {
    MandateDeadlines deadlines = service.getDeadlines();

    assertNotNull(deadlines);
    assertEquals(Instant.parse("2021-03-31T23:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-03-31T23:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2021-05-03"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2021-07-31T23:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-09-01"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-03-31T23:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-04-16"), deadlines.getWithdrawalFulfillmentDate());
  }

  @Test
  void canGetMandateDeadlines_withSpecificApplicationDate() {
    Instant specificApplicationDate = Instant.parse("2021-03-11T10:00:00Z");
    MandateDeadlines deadlines = service.getDeadlines(specificApplicationDate);

    assertNotNull(deadlines);
    assertEquals(Instant.parse("2021-03-31T23:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-03-31T23:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2021-05-03"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2021-07-31T23:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-09-01"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-03-31T23:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-04-16"), deadlines.getWithdrawalFulfillmentDate());
  }

  @Test
  void canGetCurrentPeriodStartDate() {
    LocalDate expectedCurrentPeriodStart = LocalDate.parse("2020-12-01");
    assertEquals(expectedCurrentPeriodStart, service.getCurrentPeriodStartDate());
  }

  @Test
  void canGetPeriodStartDateForSpecificDate() {
    LocalDate dateInMidPeriod = LocalDate.parse("2021-05-15");
    LocalDate expectedPeriodStart = LocalDate.parse("2021-04-01");
    assertEquals(expectedPeriodStart, service.getPeriodStartDate(dateInMidPeriod));

    LocalDate lastDayOfPeriod = LocalDate.parse("2021-07-31");
    assertEquals(expectedPeriodStart, service.getPeriodStartDate(lastDayOfPeriod));

    LocalDate firstDayOfNewPeriod = LocalDate.parse("2021-08-01");
    LocalDate newPeriodStart = LocalDate.parse("2021-08-01");
    assertEquals(newPeriodStart, service.getPeriodStartDate(firstDayOfNewPeriod));
  }
}
