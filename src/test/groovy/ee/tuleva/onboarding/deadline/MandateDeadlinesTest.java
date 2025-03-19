package ee.tuleva.onboarding.deadline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

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
    Instant applicationDate = Instant.parse("2021-04-15T10:00:00Z");
    Clock clock = Clock.fixed(applicationDate, ZoneId.of("Europe/Tallinn"));
    MandateDeadlines deadlines = new MandateDeadlines(clock, publicHolidays, applicationDate);

    assertEquals(LocalDate.parse("2021-04-01"), deadlines.getCurrentPeriodStartDate());
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
}
