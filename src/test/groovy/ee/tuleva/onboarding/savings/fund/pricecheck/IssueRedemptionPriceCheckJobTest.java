package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.UnitDealing;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueRedemptionPriceCheckJobTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.001");
  private static final BigDecimal NAV = new BigDecimal("1.1000");
  private static final BookingDays WEDNESDAYS_BOOKINGS =
      new BookingDays(LocalDate.parse("2026-11-25"), LocalDate.parse("2026-11-26"));

  @Mock IssueRedemptionPriceCheck check;
  @Mock OperationsNotificationService notificationService;

  IssueRedemptionPriceCheckJob job;

  @BeforeEach
  void setUp() {
    job = jobRunningAt("2026-11-26T06:00:00Z");
  }

  @Test
  void postsTheDealsNotAtTheirNavToTheInvestmentChannel() {
    var run = run(deal("120.00"));
    given(check.run(WEDNESDAYS_BOOKINGS)).willReturn(run);

    job.checkThePreviousWorkingDaysDeals();

    verify(notificationService).sendMessage(run.alert().orElseThrow(), INVESTMENT);
  }

  @Test
  void staysQuietWhenEveryDealIsAtItsNav() {
    given(check.run(WEDNESDAYS_BOOKINGS)).willReturn(run(deal("110.00")));

    job.checkThePreviousWorkingDaysDeals();

    verifyNoInteractions(notificationService);
  }

  @Test
  void staysQuietWhenADealIsOffItsNavOnlyByRounding() {
    var tinyRedemptionRoundedToTheCent =
        new DealingPrice(
            new UnitDealing(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                REDEMPTION_REQUEST,
                Instant.parse("2026-11-25T14:05:00Z"),
                new BigDecimal("0.33333"),
                new BigDecimal("0.37"),
                NAV,
                LocalDate.parse("2026-11-24")),
            NAV);
    given(check.run(WEDNESDAYS_BOOKINGS)).willReturn(run(tinyRedemptionRoundedToTheCent));

    job.checkThePreviousWorkingDaysDeals();

    verifyNoInteractions(notificationService);
  }

  @Test
  void saysTheCheckCouldNotRunWhenItFails() {
    given(check.run(WEDNESDAYS_BOOKINGS))
        .willThrow(new IllegalStateException("Investment parameter missing"));

    job.checkThePreviousWorkingDaysDeals();

    verify(notificationService)
        .sendMessage(
            """
            🛑 TKF100 issue/redemption price check could not run for deals booked 2026-11-25: error=Investment parameter missing
            Next: check those deals' prices against their NAV by hand and fix what stopped the check""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void doesNotRunOnAPublicHoliday() {
    jobRunningAt("2026-12-24T06:00:00Z").checkThePreviousWorkingDaysDeals();

    verifyNoInteractions(check, notificationService);
  }

  private IssueRedemptionPriceCheckJob jobRunningAt(String instant) {
    return new IssueRedemptionPriceCheckJob(
        check,
        notificationService,
        new PublicHolidays(),
        Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
  }

  private static PriceCheckRun run(DealingPrice price) {
    return new PriceCheckRun(WEDNESDAYS_BOOKINGS, THRESHOLD, List.of(price));
  }

  private static DealingPrice deal(String cash) {
    return new DealingPrice(
        new UnitDealing(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            REDEMPTION_REQUEST,
            Instant.parse("2026-11-25T14:05:00Z"),
            new BigDecimal("100.00000"),
            new BigDecimal(cash),
            NAV,
            LocalDate.parse("2026-11-24")),
        NAV);
  }
}
