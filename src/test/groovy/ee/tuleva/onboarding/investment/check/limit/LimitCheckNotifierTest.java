package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.check.limit.LimitCheckRun.UnfilledGap;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitCheckNotifierTest {

  @Mock OperationsNotificationService notificationService;
  @InjectMocks LimitCheckNotifier notifier;

  @Test
  void sendsBreachNotificationToInvestmentChannel() {
    var breach =
        new PositionBreach(
            TUK75,
            "IE001",
            "iShares MSCI World",
            new BigDecimal("16.50"),
            new BigDecimal("15"),
            new BigDecimal("20"),
            SOFT);
    var result =
        new LimitCheckResult(
            TUK75, LocalDate.of(2026, 3, 4), List.of(breach), List.of(), null, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService).sendMessage(contains("LIMIT BREACH"), eq(INVESTMENT));
  }

  // The gap fill replays dates that can be weeks old, and the message is the only place the reader
  // sees which day a breach is about. Without the date, a three-week-old HARD breach is word for
  // word the message a live one would send.
  @Test
  void aBreachLineSaysWhichDayItIsAbout() {
    var breach =
        new PositionBreach(
            TUK75,
            "IE001",
            "iShares MSCI World",
            new BigDecimal("21.50"),
            new BigDecimal("15"),
            new BigDecimal("20"),
            HARD);
    var result =
        new LimitCheckResult(
            TUK75, LocalDate.of(2026, 2, 11), List.of(breach), List.of(), null, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService)
        .sendMessage(contains("[HARD] POSITION TUK75 2026-02-11"), eq(INVESTMENT));
  }

  // Nothing else will ever mention a day the limit check could not cover: the gap is not in any
  // event row and no other job looks for it. So it is named every evening, and once it has stood
  // longer than a working week it also says how long, and when it will stop being attempted.
  @Test
  void aStandingGapIsNamedWithItsAgeAndItsLastAttempt() {
    var run =
        new LimitCheckRun(
            List.of(),
            List.of(),
            List.of(
                new UnfilledGap(TUK75, LocalDate.of(2026, 2, 10), 22, LocalDate.of(2026, 3, 12))));

    notifier.notify(run);

    verify(notificationService)
        .sendMessage(
            contains("TUK75 2026-02-10 — standing gap: open for 22 days, last attempt 2026-03-12"),
            eq(INVESTMENT));
  }

  @Test
  void aGapFromTonightIsNamedWithoutTheStandingGapWording() {
    var run =
        new LimitCheckRun(
            List.of(),
            List.of(),
            List.of(new UnfilledGap(TUK75, LocalDate.of(2026, 3, 3), 1, LocalDate.of(2026, 4, 2))));

    notifier.notify(run);

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue()).contains("TUK75 2026-03-03").doesNotContain("standing gap");
  }

  @Test
  void sendsAllClearWhenNoBreaches() {
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService).sendMessage(contains("TUK75 within limits"), eq(INVESTMENT));
  }

  // A fill covering several days returns one result per fund per day, and naming the fund once per
  // result repeats the same four codes down the message without saying which days were covered.
  @Test
  void anAllClearOverSeveralDaysNamesEachFundOnceAndSaysWhichDaysItCovered() {
    var run =
        LimitCheckRun.of(
            List.of(
                new LimitCheckResult(
                    TUK75, LocalDate.of(2026, 3, 2), List.of(), List.of(), null, null),
                new LimitCheckResult(
                    TUK75, LocalDate.of(2026, 3, 3), List.of(), List.of(), null, null),
                new LimitCheckResult(
                    TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, null)));

    notifier.notify(run);

    verify(notificationService)
        .sendMessage(
            contains("TUK75 within limits on 3 dates, 2026-03-02 to 2026-03-04"), eq(INVESTMENT));
  }

  @Test
  void includesProviderBreachDetails() {
    var breach =
        new ProviderBreach(
            TUK75, ISHARES, new BigDecimal("35"), new BigDecimal("30"), new BigDecimal("40"), SOFT);
    var result =
        new LimitCheckResult(
            TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(breach), null, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService).sendMessage(contains("ISHARES"), eq(INVESTMENT));
  }

  @Test
  void includesReserveBreachDetails() {
    var breach =
        new ReserveBreach(
            TUK75, new BigDecimal("40000"), new BigDecimal("50000"), new BigDecimal("30000"), SOFT);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), breach, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService).sendMessage(contains("RESERVE"), eq(INVESTMENT));
  }

  @Test
  void freeCashBreachDoesNotTriggerNotification() {
    var breach = new FreeCashBreach(TUK75, new BigDecimal("25000"), new BigDecimal("10000"), HARD);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, breach);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService).sendMessage(contains("within limits"), eq(INVESTMENT));
  }

  @Test
  void withinLimitsMessageShowsGreenIcon() {
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    verify(notificationService).sendMessage(contains("✅"), eq(INVESTMENT));
  }

  @Test
  void softBreachShowsYellowIcon() {
    var breach =
        new ReserveBreach(
            TUK75, new BigDecimal("40000"), new BigDecimal("50000"), new BigDecimal("30000"), SOFT);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), breach, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    var message = captor.getValue();
    assertThat(message).contains("⚠️ LIMIT BREACH DETECTED");
    assertThat(message).contains("⚠️ [SOFT] RESERVE");
    assertThat(message).doesNotContain("🛑");
  }

  @Test
  void hardBreachShowsRedIconInHeaderAndLine() {
    var breach =
        new ReserveBreach(
            TUK75, new BigDecimal("20000"), new BigDecimal("50000"), new BigDecimal("30000"), HARD);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), breach, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    var message = captor.getValue();
    assertThat(message).contains("🛑 LIMIT BREACH DETECTED");
    assertThat(message).contains("🛑 [HARD] RESERVE");
  }

  @Test
  void headerReflectsWorstSeverityWhenSoftAndHardMixed() {
    var soft =
        new ProviderBreach(
            TUK75, ISHARES, new BigDecimal("35"), new BigDecimal("30"), new BigDecimal("40"), SOFT);
    var hard =
        new ReserveBreach(
            TUK75, new BigDecimal("20000"), new BigDecimal("50000"), new BigDecimal("30000"), HARD);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(soft), hard, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(notificationService).sendMessage(captor.capture(), eq(INVESTMENT));
    var message = captor.getValue();
    assertThat(message).contains("🛑 LIMIT BREACH DETECTED");
    assertThat(message).contains("⚠️ [SOFT] PROVIDER");
    assertThat(message).contains("🛑 [HARD] RESERVE");
  }

  @Test
  void swallowsExceptions() {
    doThrow(new RuntimeException("Slack down")).when(notificationService).sendMessage(any(), any());
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, null);

    notifier.notify(LimitCheckRun.of(List.of(result)));
  }
}
