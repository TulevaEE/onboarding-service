package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelRebalanceCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  private static final LocalDate CHRISTMAS_EVE = LocalDate.of(2026, 12, 24);

  private final RebalanceWindows rebalanceWindows = mock(RebalanceWindows.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final EstonianCalendar estonianCalendar = new EstonianCalendar(new PublicHolidays());

  @Test
  void aFundWhoseCheckCannotRun_isReportedAndTheOtherFundsAreStillChecked() {
    given(rebalanceWindows.current(TUK75, MONDAY))
        .willThrow(new IllegalStateException("No investment parameter found"));
    given(rebalanceWindows.current(TUK00, MONDAY)).willReturn(Optional.empty());
    given(rebalanceWindows.current(TUV100, MONDAY)).willReturn(Optional.of(warningWindow()));
    given(rebalanceWindows.current(TKF100, MONDAY)).willReturn(Optional.empty());

    jobOn(MONDAY).checkRebalanceWindows();

    then(notificationService)
        .should()
        .sendMessage(
            "⏸ TUK75 mudelportfelli tasakaalustamise kontroll ei jooksnud; põhjus on rakenduse"
                + " logis, kontroll proovib järgmisel tööpäeval uuesti",
            INVESTMENT,
            ERROR);
    then(notificationService)
        .should()
        .sendMessage(startsWith("⚠️ TUV100"), eq(INVESTMENT), eq(WARNING));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aSlackPostThatFails_doesNotStopTheOtherFunds() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.of(warningWindow()));
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(any(), eq(INVESTMENT), eq(WARNING));

    jobOn(MONDAY).checkRebalanceWindows();

    then(rebalanceWindows).should().current(TKF100, MONDAY);
  }

  @Test
  void aPublicHoliday_checksNothingAndSendsNothing() {
    jobOn(CHRISTMAS_EVE).checkRebalanceWindows();

    then(rebalanceWindows).shouldHaveNoInteractions();
    then(notificationService).shouldHaveNoInteractions();
  }

  private ModelRebalanceCheckJob jobOn(LocalDate date) {
    var clock = Clock.fixed(date.atTime(9, 15).atZone(TALLINN).toInstant(), TALLINN);
    return new ModelRebalanceCheckJob(
        rebalanceWindows, notificationService, estonianCalendar, clock);
  }

  private static RebalanceWindow warningWindow() {
    return new RebalanceWindow(
        TUV100,
        LocalDate.of(2026, 10, 1),
        MONDAY,
        new BigDecimal("0.05"),
        Set.of("IE00BFG1TM61"),
        Set.of(),
        null,
        List.of(),
        null,
        new RebalanceDeadlines(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 4)));
  }
}
