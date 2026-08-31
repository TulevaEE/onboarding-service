package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FundPositionTransactionDateTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Test
  void usesRealTimestampWhenProcessedOnNextWorkingDayBeforeCutoff() {
    Instant now = Instant.parse("2026-02-04T07:23:15Z");

    assertThat(transactionDate(now, TKF100, LocalDate.of(2026, 2, 3))).isEqualTo(now);
  }

  @Test
  void usesFallbackTimestampWhenBackfilling() {
    Instant now = Instant.parse("2026-02-25T08:00:00Z");

    assertThat(transactionDate(now, TKF100, LocalDate.of(2026, 2, 3)))
        .isEqualTo(LocalDate.of(2026, 2, 4).atTime(10, 0).atZone(ESTONIAN_ZONE).toInstant());
  }

  @Test
  void usesFixedTimestampWhenProcessedAfterCutoff() {
    Instant now = Instant.parse("2026-02-04T14:30:00Z");

    assertThat(transactionDate(now, TKF100, LocalDate.of(2026, 2, 3)))
        .isEqualTo(LocalDate.of(2026, 2, 4).atTime(10, 0).atZone(ESTONIAN_ZONE).toInstant());
  }

  @Test
  void usesReportDateMorningForInceptionDay() {
    Instant now = Instant.parse("2026-02-25T08:00:00Z");
    LocalDate inceptionDate = TKF100.getInceptionDate();

    assertThat(transactionDate(now, TKF100, inceptionDate))
        .isEqualTo(inceptionDate.atTime(10, 0).atZone(ESTONIAN_ZONE).toInstant());
  }

  @Test
  void skipsWeekendForFridayReport() {
    Instant now = Instant.parse("2026-02-25T08:00:00Z");

    assertThat(transactionDate(now, TKF100, LocalDate.of(2026, 2, 6)))
        .isEqualTo(LocalDate.of(2026, 2, 9).atTime(10, 0).atZone(ESTONIAN_ZONE).toInstant());
  }

  @Test
  void usesNowWhenReportDateIsToday() {
    Instant now = Instant.parse("2026-02-25T08:00:00Z");

    assertThat(transactionDate(now, TKF100, LocalDate.of(2026, 2, 25))).isEqualTo(now);
  }

  private Instant transactionDate(Instant now, TulevaFund fund, LocalDate reportDate) {
    var service =
        new FundPositionLedgerService(
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            new PublicHolidays(),
            Clock.fixed(now, ESTONIAN_ZONE));
    return service.transactionDate(fund, reportDate);
  }
}
