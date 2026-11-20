package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
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
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelRebalanceCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
  private static final Instant MONDAY_RUN = MONDAY.atTime(9, 15).atZone(TALLINN).toInstant();
  private static final LocalDate CHRISTMAS_EVE = LocalDate.of(2026, 12, 24);

  private final RebalanceWindows rebalanceWindows = mock(RebalanceWindows.class);
  private final RebalanceAnnouncements announcements = mock(RebalanceAnnouncements.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final EstonianCalendar estonianCalendar = new EstonianCalendar(new PublicHolidays());

  @BeforeEach
  void noFundHasAWindowUnlessATestSaysSo() {
    given(rebalanceWindows.of(any(), eq(MONDAY), any())).willReturn(FundRebalanceWindows.NONE);
  }

  @Test
  void aFundWhoseWindowsCannotBeLoaded_getsOneNoticeAndTheOtherFundsAreStillChecked() {
    given(rebalanceWindows.of(eq(TUK75), eq(MONDAY), any()))
        .willThrow(new IllegalStateException("No investment parameter found"));
    givenWindows(TUV100, warningWindow());
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
  void anOpenWindowsPostThatFails_isNotRecordedSoTheNextRunPostsItAndTheOtherFundsAreChecked() {
    given(rebalanceWindows.of(any(), eq(MONDAY), any()))
        .willReturn(new FundRebalanceWindows(Optional.of(warningWindow()), List.of()));
    given(announcements.isOwed(warningWindow())).willReturn(true);
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(any(), eq(INVESTMENT), eq(WARNING));

    jobOn(MONDAY).checkRebalanceWindows();

    then(rebalanceWindows).should().of(eq(TKF100), eq(MONDAY), any());
    then(announcements).should(never()).recordOpen(any(), any());
  }

  @Test
  void anOpenWindowsAnnouncementIsRecordedOnlyOnceSlackHasAcceptedIt() {
    givenWindows(TUV100, warningWindow());
    given(announcements.isOwed(warningWindow())).willReturn(true);

    jobOn(MONDAY).checkRebalanceWindows();

    var inOrder = inOrder(notificationService, announcements);
    then(notificationService)
        .should(inOrder)
        .sendMessage(startsWith("⚠️ TUV100"), eq(INVESTMENT), eq(WARNING));
    then(announcements).should(inOrder).recordOpen(warningWindow(), MONDAY_RUN);
  }

  @Test
  void anOpenAnnouncementWhoseRecordFailsAfterPosting_isFollowedByOneNoticeForTheFund() {
    givenWindows(TUV100, warningWindow());
    given(announcements.isOwed(warningWindow())).willReturn(true);
    willThrow(new IllegalStateException("event_log unavailable"))
        .given(announcements)
        .recordOpen(warningWindow(), MONDAY_RUN);

    jobOn(MONDAY).checkRebalanceWindows();

    var inOrder = inOrder(notificationService);
    then(notificationService)
        .should(inOrder)
        .sendMessage(startsWith("⚠️ TUV100"), eq(INVESTMENT), eq(WARNING));
    then(notificationService)
        .should(inOrder)
        .sendMessage(startsWith("⏸ TUV100"), eq(INVESTMENT), eq(ERROR));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aCloseIsRecordedOnlyOnceSlackHasAcceptedIt() {
    givenWindows(TUV100, closedWindow());
    given(announcements.isOwed(closedWindow())).willReturn(true);

    jobOn(MONDAY).checkRebalanceWindows();

    var inOrder = inOrder(notificationService, announcements);
    then(notificationService)
        .should(inOrder)
        .sendMessage(startsWith("✅ TUV100"), eq(INVESTMENT), eq(INFO));
    then(announcements).should(inOrder).recordClose(closedWindow(), MONDAY_RUN);
  }

  @Test
  void aCloseThatCouldNotBePosted_isNotRecordedSoTheNextRunPostsIt() {
    givenWindows(TUV100, closedWindow());
    given(announcements.isOwed(closedWindow())).willReturn(true);
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(any(), eq(INVESTMENT), eq(INFO));

    jobOn(MONDAY).checkRebalanceWindows();

    then(announcements).should(never()).recordClose(any(), any());
  }

  @Test
  void aWindowWithNothingOwed_sendsAndRecordsNothing() {
    givenWindows(TUV100, warningWindow());
    given(announcements.isOwed(warningWindow())).willReturn(false);

    jobOn(MONDAY).checkRebalanceWindows();

    then(notificationService).shouldHaveNoInteractions();
    then(announcements).should(never()).recordOpen(any(), any());
  }

  @Test
  void anOwedCloseOfASupersededWindow_isAnnouncedAndRecordedBeforeTheCurrentWindow() {
    given(rebalanceWindows.of(eq(TUV100), eq(MONDAY), any()))
        .willReturn(
            new FundRebalanceWindows(
                Optional.of(warningWindow()),
                List.of(new SupersededWindow(closedWindow(), LocalDate.of(2026, 10, 1)))));
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
    then(announcements).should().recordClose(closedWindow(), MONDAY_RUN);
  }

  @Test
  void aSupersededWindowThatNeverCameIntoLine_isClosedWithAReplacedLineAndRecorded() {
    given(rebalanceWindows.of(eq(TUV100), eq(MONDAY), any()))
        .willReturn(
            new FundRebalanceWindows(
                Optional.empty(),
                List.of(new SupersededWindow(warningWindow(), LocalDate.of(2026, 11, 2)))));

    jobOn(MONDAY).checkRebalanceWindows();

    var inOrder = inOrder(notificationService, announcements);
    then(notificationService)
        .should(inOrder)
        .sendMessage(
            startsWith("⏹ TUV100 mudelportfell alates 01.10.2026: asendatud mudeliga alates"),
            eq(INVESTMENT),
            eq(INFO));
    then(announcements).should(inOrder).recordClose(warningWindow(), MONDAY_RUN);
  }

  @Test
  void aPublicHoliday_checksNothingAndSendsNothing() {
    jobOn(CHRISTMAS_EVE).checkRebalanceWindows();

    then(rebalanceWindows).shouldHaveNoInteractions();
    then(notificationService).shouldHaveNoInteractions();
  }

  private void givenWindows(TulevaFund fund, RebalanceWindow current) {
    given(rebalanceWindows.of(eq(fund), eq(MONDAY), any()))
        .willReturn(new FundRebalanceWindows(Optional.of(current), List.of()));
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
