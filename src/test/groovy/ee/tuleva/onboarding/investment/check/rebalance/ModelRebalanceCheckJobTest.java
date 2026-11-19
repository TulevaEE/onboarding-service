package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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
  private final RebalanceAnnouncements announcements = mock(RebalanceAnnouncements.class);
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
    given(announcements.isOwed(warningWindow())).willReturn(true);

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
  void aSlackPostThatFails_isNotRecordedAndDoesNotStopTheOtherFunds() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.of(warningWindow()));
    given(announcements.isOwed(warningWindow())).willReturn(true);
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(any(), eq(INVESTMENT), eq(WARNING));

    jobOn(MONDAY).checkRebalanceWindows();

    then(rebalanceWindows).should().current(TKF100, MONDAY);
    then(announcements).should(never()).recordAnnounced(any(), any());
  }

  @Test
  void aPostedAnnouncement_isRecordedWithTheTimeItWasPosted() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.empty());
    given(rebalanceWindows.current(TUV100, MONDAY)).willReturn(Optional.of(warningWindow()));
    given(announcements.isOwed(warningWindow())).willReturn(true);

    jobOn(MONDAY).checkRebalanceWindows();

    then(announcements)
        .should()
        .recordAnnounced(warningWindow(), MONDAY.atTime(9, 15).atZone(TALLINN).toInstant());
  }

  @Test
  void aWindowWithNothingOwed_sendsAndRecordsNothing() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.empty());
    given(rebalanceWindows.current(TUV100, MONDAY)).willReturn(Optional.of(warningWindow()));
    given(announcements.isOwed(warningWindow())).willReturn(false);

    jobOn(MONDAY).checkRebalanceWindows();

    then(notificationService).shouldHaveNoInteractions();
    then(announcements).should(never()).recordAnnounced(any(), any());
  }

  @Test
  void anOwedCloseOfASupersededWindow_isAnnouncedAndRecordedBeforeTheCurrentWindow() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.empty());
    given(rebalanceWindows.current(TUV100, MONDAY)).willReturn(Optional.of(warningWindow()));
    given(rebalanceWindows.supersededWindows(eq(TUV100), eq(MONDAY), any()))
        .willReturn(List.of(closedWindow()));
    given(announcements.isOwed(warningWindow())).willReturn(true);

    jobOn(MONDAY).checkRebalanceWindows();

    var inOrder = inOrder(notificationService);
    then(notificationService)
        .should(inOrder)
        .sendMessage(
            startsWith("✅ TUV100 mudelportfell alates 01.06.2026"), eq(INVESTMENT), eq(INFO));
    then(notificationService)
        .should(inOrder)
        .sendMessage(
            startsWith("⚠️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(WARNING));
    then(announcements).should().recordAnnounced(eq(closedWindow()), any());
  }

  @Test
  void aSupersededWindowThatNeverClosed_sendsNothingForItself() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.empty());
    given(rebalanceWindows.current(TUV100, MONDAY)).willReturn(Optional.of(warningWindow()));
    given(rebalanceWindows.supersededWindows(eq(TUV100), eq(MONDAY), any()))
        .willReturn(List.of(warningWindowOf(LocalDate.of(2026, 6, 1))));
    given(announcements.isOwed(warningWindow())).willReturn(true);

    jobOn(MONDAY).checkRebalanceWindows();

    then(notificationService)
        .should()
        .sendMessage(
            startsWith("⚠️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(WARNING));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aSupersededWindowThatCannotBeMeasured_isReportedAndTheCurrentWindowIsStillAnnounced() {
    given(rebalanceWindows.current(any(), eq(MONDAY))).willReturn(Optional.empty());
    given(rebalanceWindows.current(TUV100, MONDAY)).willReturn(Optional.of(warningWindow()));
    given(rebalanceWindows.supersededWindows(eq(TUV100), eq(MONDAY), any()))
        .willThrow(new IllegalStateException("No investment parameter found"));
    given(announcements.isOwed(warningWindow())).willReturn(true);

    jobOn(MONDAY).checkRebalanceWindows();

    then(notificationService)
        .should()
        .sendMessage(startsWith("⏸ TUV100"), eq(INVESTMENT), eq(ERROR));
    then(notificationService)
        .should()
        .sendMessage(
            startsWith("⚠️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(WARNING));
    then(notificationService).shouldHaveNoMoreInteractions();
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
        rebalanceWindows, announcements, notificationService, estonianCalendar, clock);
  }

  private static RebalanceWindow closedWindow() {
    return new RebalanceWindow(
        TUV100,
        LocalDate.of(2026, 6, 1),
        LocalDate.of(2026, 9, 30),
        new BigDecimal("0.05"),
        Set.of("IE00BFG1TM61"),
        Set.of("IE00BFG1TM61"),
        LocalDate.of(2026, 9, 29),
        List.of(),
        LocalDate.of(2026, 9, 29),
        new RebalanceDeadlines(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)));
  }

  private static RebalanceWindow warningWindow() {
    return warningWindowOf(LocalDate.of(2026, 10, 1));
  }

  private static RebalanceWindow warningWindowOf(LocalDate modelEffectiveDate) {
    return new RebalanceWindow(
        TUV100,
        modelEffectiveDate,
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
