package ee.tuleva.onboarding.deadline;

import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MandateDeadlinesTest {

  private final PublicHolidays publicHolidays = new PublicHolidays();

  @Test
  void testMandateDeadlinesBeforeMarch31() {
    Instant applicationDate = Instant.parse("2021-03-31T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2021-03-31T20:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-03-31T20:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2021-05-03"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2021-07-31T20:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-09-01"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-03-31T20:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-04-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2021-04-20"), deadlines.getWithdrawalLatestFulfillmentDate());
    assertEquals(LocalDate.parse("2021-09-01"), deadlines.getSecondPillarContributionEndDate());
  }

  @Test
  void testMandateDeadlinesAfterMarch31() {
    Instant applicationDate = Instant.parse("2021-04-01T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2021-07-31T20:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-07-31T20:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2021-09-01"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2021-11-30T21:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2022-01-03"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-04-30T20:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-05-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2021-05-20"), deadlines.getWithdrawalLatestFulfillmentDate());
    assertEquals(LocalDate.parse("2022-01-01"), deadlines.getSecondPillarContributionEndDate());
  }

  @Test
  void testMandateDeadlinesAfter31July() {
    Instant applicationDate = Instant.parse("2021-08-11T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2021-11-30T21:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-11-30T21:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2022-01-03"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2022-03-31T20:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2022-05-02"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-08-31T20:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-09-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2021-09-20"), deadlines.getWithdrawalLatestFulfillmentDate());
    assertEquals(LocalDate.parse("2022-05-01"), deadlines.getSecondPillarContributionEndDate());
  }

  @Test
  void testMandateDeadlinesAfter30November() {
    Instant applicationDate = Instant.parse("2021-12-01T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2022-03-31T20:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2022-03-31T20:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2022-05-02"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2022-07-31T20:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2022-09-01"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-12-31T21:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2022-01-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2022-01-20"), deadlines.getWithdrawalLatestFulfillmentDate());
    assertEquals(LocalDate.parse("2022-09-01"), deadlines.getSecondPillarContributionEndDate());
  }

  @Test
  void testFulfillmentDatesForDifferentApplicationTypes() {
    Instant applicationDate = Instant.parse("2021-08-11T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(
        deadlines.getTransferMandateFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.TRANSFER));
    assertEquals(
        deadlines.getEarlyWithdrawalFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.EARLY_WITHDRAWAL));
    assertEquals(
        deadlines.getWithdrawalLatestFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.WITHDRAWAL));
  }

  @Test
  void testPreviouslyDoneMandateDeadlinesAfterMarch31() {
    Instant applicationDate = Instant.parse("2021-03-31T10:00:00Z");
    Clock clock = Clock.fixed(Instant.parse("2021-04-01T10:00:00Z"), ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2021-03-31T20:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-03-31T20:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2021-05-03"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2021-07-31T20:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-09-01"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-03-31T20:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-04-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2021-04-20"), deadlines.getWithdrawalLatestFulfillmentDate());
  }

  @Test
  void testMandateDeadlinesAfterYearChange() {
    Instant applicationDate = Instant.parse("2021-10-10T10:00:00Z");
    Clock clock = Clock.fixed(Instant.parse("2022-01-05T10:00:00Z"), ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2021-11-30T21:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2021-11-30T21:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2022-01-03"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2022-03-31T20:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2022-05-02"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2021-10-31T21:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2021-11-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2021-11-20"), deadlines.getWithdrawalLatestFulfillmentDate());
  }

  @Test
  void testMandateDeadlinesAcrossYearBoundaries() {
    Instant applicationDate = Instant.parse("2023-11-30T10:00:00Z");
    Clock clock = Clock.fixed(Instant.parse("2023-11-30T10:00:00Z"), ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(Instant.parse("2023-11-30T21:59:59.999999999Z"), deadlines.getPeriodEnding());
    assertEquals(
        Instant.parse("2023-11-30T21:59:59.999999999Z"),
        deadlines.getTransferMandateCancellationDeadline());
    assertEquals(LocalDate.parse("2024-01-02"), deadlines.getTransferMandateFulfillmentDate());
    assertEquals(
        Instant.parse("2024-03-31T20:59:59.999999999Z"),
        deadlines.getEarlyWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2024-05-02"), deadlines.getEarlyWithdrawalFulfillmentDate());
    assertEquals(
        Instant.parse("2023-11-30T21:59:59.999999999Z"),
        deadlines.getWithdrawalCancellationDeadline());
    assertEquals(LocalDate.parse("2023-12-16"), deadlines.getWithdrawalFulfillmentDate());
    assertEquals(LocalDate.parse("2023-12-20"), deadlines.getWithdrawalLatestFulfillmentDate());
  }

  @Test
  void testCancellationDeadlineSwitchCases() {
    Instant applicationDate = Instant.parse("2021-08-11T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(
        deadlines.getTransferMandateCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.TRANSFER));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.WITHDRAWAL));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.FUND_PENSION_OPENING));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.FUND_PENSION_OPENING_THIRD_PILLAR));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.PARTIAL_WITHDRAWAL));
    assertEquals(
        deadlines.getNonCancellableApplicationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.WITHDRAWAL_THIRD_PILLAR));
    assertEquals(
        deadlines.getEarlyWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.EARLY_WITHDRAWAL));
    assertEquals(
        deadlines.getPaymentRateDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.PAYMENT_RATE));
  }

  @Test
  void testPaymentRateDeadlineBeforeNovember30() {
    Instant applicationDate = Instant.parse("2024-11-29T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(
        Instant.parse("2024-11-30T21:59:59.999999999Z"), deadlines.getPaymentRateDeadline());
  }

  @Test
  void testPaymentRateDeadlineAfterNovember30() {
    Instant applicationDate = Instant.parse("2024-12-01T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(
        Instant.parse("2025-11-30T21:59:59.999999999Z"), deadlines.getPaymentRateDeadline());
  }

  @Test
  void testPaymentRateFulfillmentDateBeforeNovember30() {
    Instant applicationDate = Instant.parse("2024-11-29T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(LocalDate.parse("2025-01-01"), deadlines.getPaymentRateFulfillmentDate());
  }

  @Test
  void testPaymentRateFulfillmentDateAfterNovember30() {
    Instant applicationDate = Instant.parse("2024-12-01T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(LocalDate.parse("2026-01-01"), deadlines.getPaymentRateFulfillmentDate());
  }

  @Test
  void testGetCurrentPeriodStartDateWhenPeriodEndingIsJuly31() {
    Instant applicationDate =
        Instant.parse("2021-04-15T10:00:00Z"); // Application after Mar 31, before Jul 31
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    assertEquals(LocalDate.parse("2021-04-01"), deadlines.getCurrentPeriodStartDate());
  }

  @Test
  void testGetCurrentPeriodStartDateWhenPeriodEndingIsNovember30() {
    Instant applicationDate = Instant.parse("2021-08-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    assertEquals(LocalDate.parse("2021-08-01"), deadlines.getCurrentPeriodStartDate());
  }

  @Test
  void testGetCurrentPeriodStartDateWhenPeriodEndingIsMarch31NextYear() {
    Instant applicationDate = Instant.parse("2021-12-01T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    assertEquals(LocalDate.parse("2021-12-01"), deadlines.getCurrentPeriodStartDate());
  }

  @Test
  void testGetFulfillmentDateSwitchCases() {
    Instant applicationDate = Instant.parse("2021-08-11T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(
        deadlines.getTransferMandateFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.TRANSFER));
    assertEquals(
        deadlines.getWithdrawalLatestFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.WITHDRAWAL));
    assertEquals(
        deadlines.getWithdrawalLatestFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.FUND_PENSION_OPENING));
    assertEquals(
        deadlines.getWithdrawalLatestFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.FUND_PENSION_OPENING_THIRD_PILLAR));
    assertEquals(
        deadlines.getWithdrawalLatestFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.PARTIAL_WITHDRAWAL));
    assertEquals(
        deadlines.getThirdPillarWithdrawalFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.WITHDRAWAL_THIRD_PILLAR));
    assertEquals(
        deadlines.getEarlyWithdrawalFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.EARLY_WITHDRAWAL));
    assertEquals(
        deadlines.getPaymentRateFulfillmentDate(),
        deadlines.getFulfillmentDate(ApplicationType.PAYMENT_RATE));
  }

  @Test
  void testGetCancellationDeadlineSwitchCases() {
    Instant applicationDate = Instant.parse("2021-08-11T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(
        deadlines.getTransferMandateCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.TRANSFER));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.WITHDRAWAL));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.FUND_PENSION_OPENING));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.FUND_PENSION_OPENING_THIRD_PILLAR));
    assertEquals(
        deadlines.getWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.PARTIAL_WITHDRAWAL));
    assertEquals(
        deadlines.getNonCancellableApplicationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.WITHDRAWAL_THIRD_PILLAR));
    assertEquals(
        deadlines.getEarlyWithdrawalCancellationDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.EARLY_WITHDRAWAL));
    assertEquals(
        deadlines.getPaymentRateDeadline(),
        deadlines.getCancellationDeadline(ApplicationType.PAYMENT_RATE));
  }

  @Test
  void testNonCancellableApplicationDeadline() {
    Instant applicationDate = Instant.parse("2021-06-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    assertEquals(applicationDate, deadlines.getNonCancellableApplicationDeadline());
  }

  @Test
  void getFulfillmentDate_withNullApplicationType_throwsIllegalArgumentException() {
    Instant applicationDate = Instant.parse("2021-01-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> deadlines.getFulfillmentDate(null),
            "Expected getFulfillmentDate(null) to throw IllegalArgumentException");
    assertEquals("Application type cannot be null", exception.getMessage());
  }

  @Test
  void getCancellationDeadline_withNullApplicationType_throwsIllegalArgumentException() {
    Instant applicationDate = Instant.parse("2021-01-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> deadlines.getCancellationDeadline(null),
            "Expected getCancellationDeadline(null) to throw IllegalArgumentException");
    assertEquals("Application type cannot be null", exception.getMessage());
  }

  @Test
  void getFulfillmentDate_withUnknownApplicationType_throwsIllegalArgumentException() {
    Instant applicationDate = Instant.parse("2021-01-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    ApplicationType unknownType = ApplicationType.SELECTION;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> deadlines.getFulfillmentDate(unknownType));
    assertEquals("Unknown application type: " + unknownType, exception.getMessage());
  }

  @Test
  void getCancellationDeadline_withUnknownApplicationType_throwsIllegalArgumentException() {
    Instant applicationDate = Instant.parse("2021-01-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    ApplicationType unknownType = ApplicationType.SELECTION;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> deadlines.getCancellationDeadline(unknownType));
    assertEquals("Unknown application type: " + unknownType, exception.getMessage());
  }

  @Test
  void getFulfillmentDate_withCancellationApplicationType_throwsIllegalArgumentException() {
    Instant applicationDate = Instant.parse("2021-01-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    ApplicationType cancellationType = ApplicationType.CANCELLATION;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> deadlines.getFulfillmentDate(cancellationType));
    assertEquals("Unknown application type: " + cancellationType, exception.getMessage());
  }

  @Test
  void getCancellationDeadline_withCancellationApplicationType_throwsIllegalArgumentException() {
    Instant applicationDate = Instant.parse("2021-01-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);
    ApplicationType cancellationType = ApplicationType.CANCELLATION;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> deadlines.getCancellationDeadline(cancellationType));
    assertEquals("Unknown application type: " + cancellationType, exception.getMessage());
  }

  static Stream<Arguments> thirdPillarPaymentDeadlineTestCases() {
    return Stream.of(
        Arguments.of(2018, MONDAY, Instant.parse("2018-12-27T13:59:59.999999999Z")),
        Arguments.of(2024, TUESDAY, Instant.parse("2024-12-27T13:59:59.999999999Z")),
        Arguments.of(2025, WEDNESDAY, Instant.parse("2025-12-29T13:59:59.999999999Z")),
        Arguments.of(2026, THURSDAY, Instant.parse("2026-12-29T13:59:59.999999999Z")),
        Arguments.of(2027, FRIDAY, Instant.parse("2027-12-29T13:59:59.999999999Z")),
        Arguments.of(2022, SATURDAY, Instant.parse("2022-12-28T13:59:59.999999999Z")),
        Arguments.of(2028, SUNDAY, Instant.parse("2028-12-27T13:59:59.999999999Z")));
  }

  @ParameterizedTest(name = "{0}: Dec 31 is {1}")
  @MethodSource("thirdPillarPaymentDeadlineTestCases")
  @DisplayName("Third pillar payment deadline is 2 working days before last working day of year")
  void getThirdPillarPaymentDeadline_allDaysOfWeek(
      int year, DayOfWeek expectedDayOfWeek, Instant expectedDeadline) {
    LocalDate dec31 = LocalDate.of(year, 12, 31);
    assertThat(dec31.getDayOfWeek()).isEqualTo(expectedDayOfWeek);

    Instant applicationDate = Instant.parse(year + "-12-01T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertThat(deadlines.getThirdPillarPaymentDeadline()).isEqualTo(expectedDeadline);
  }
}
