package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.TransactionCostSource.PUBLISHED_OCF;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClientException;

class SlackKidCalculationNotifierTest {

  private static final Instant A_MONDAY_MORNING = Instant.parse("2026-11-02T08:30:00Z");
  private static final Instant A_TUESDAY_MORNING = Instant.parse("2026-11-03T08:30:00Z");

  private final OperationsNotificationService notificationService =
      Mockito.mock(OperationsNotificationService.class);
  private final KidCalculationRepository calculations =
      Mockito.mock(KidCalculationRepository.class);
  private final KidPrintedFiguresRepository printedFigures =
      Mockito.mock(KidPrintedFiguresRepository.class);

  @Test
  void aMondayRunThatStoredNothingRemindsOfTheMonthsWaitingForPublication() {
    given(calculations.findCompleteUnpublishedMonths(TKF100))
        .willReturn(
            List.of(complete(44, YearMonth.of(2026, 4)), complete(45, YearMonth.of(2026, 5))));

    notifierOn(A_MONDAY_MORNING).notifyRuns(List.of(KidFundRun.ran(TKF100, List.of())));

    verify(notificationService)
        .sendMessage(
            """
            TKF100 põhiteabe kuu stsenaariumid
            Avaldamata valmis kuud: 2026-04 (arvutus 44), 2026-05 (arvutus 45)
            Next: kontrolli ja avalda: agent-gateway `kid.publish` KID_FUND=TKF100\
             CALCULATION_IDS=44,45""",
            INVESTMENT,
            INFO);
  }

  @Test
  void aMondayRunThatStoredNothingWithNothingWaitingSendsNothing() {
    given(calculations.findCompleteUnpublishedMonths(TKF100)).willReturn(List.of());

    notifierOn(A_MONDAY_MORNING).notifyRuns(List.of(KidFundRun.ran(TKF100, List.of())));

    verifyNoInteractions(notificationService);
  }

  @Test
  void anotherDaysRunThatStoredNothingSendsNothingEvenWithMonthsWaiting() {
    given(calculations.findCompleteUnpublishedMonths(TKF100))
        .willReturn(List.of(complete(44, YearMonth.of(2026, 4))));

    notifierOn(A_TUESDAY_MORNING).notifyRuns(List.of(KidFundRun.ran(TKF100, List.of())));

    verifyNoInteractions(notificationService);
  }

  @Test
  void aRunThatCouldNotRunSaysSoWithTheExceptionsClassAlone() {
    notifierOn(A_TUESDAY_MORNING)
        .notifyRuns(
            List.of(KidFundRun.failed(TKF100, new IllegalStateException("database unavailable"))));

    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 põhiteabe kuu stsenaariumid: arvutus ei jooksnud: IllegalStateException
            Next: anna IT-le teada; logis on 'KID calculation failed: fund=TKF100'""",
            INVESTMENT,
            WARNING);
  }

  @Test
  void anotherDaysRunWhoseOnlyMonthFailedUnexpectedlySaysSo() {
    notifierOn(A_TUESDAY_MORNING)
        .notifyRuns(
            List.of(
                KidFundRun.ran(
                    TKF100,
                    List.of(),
                    List.of(new KidFailedMonth(YearMonth.of(2026, 10), "DuplicateKeyException")))));

    verify(notificationService)
        .sendMessage(
            """
            TKF100 põhiteabe kuu stsenaariumid
            🔴 2026-10: arvutus ei jooksnud: DuplicateKeyException
            Next: 2026-10: anna IT-le teada; logis on 'KID calculation failed: fund=TKF100, month=2026-10'""",
            INVESTMENT,
            WARNING);
  }

  @Test
  void aFailedSendIsLoggedAndDoesNotEndTheRun() {
    willThrow(new RestClientException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(any(), any(), any());

    assertThatCode(
            () ->
                notifierOn(A_TUESDAY_MORNING)
                    .notifyRuns(
                        List.of(KidFundRun.failed(TKF100, new IllegalStateException("down")))))
        .doesNotThrowAnyException();
  }

  private SlackKidCalculationNotifier notifierOn(Instant now) {
    return new SlackKidCalculationNotifier(
        notificationService,
        KidFunds.TULEVA_FUNDS,
        calculations,
        printedFigures,
        Clock.fixed(now, ZoneId.of("Europe/Tallinn")));
  }

  private static StoredKidCalculation complete(long id, YearMonth month) {
    return new StoredKidCalculation(
        id,
        TKF100,
        month,
        1,
        List.of(),
        "MSCI_ACWI",
        month.atEndOfMonth(),
        new OcfBasis(3, LocalDate.of(2026, 1, 12), new BigDecimal("0.00321500"), null),
        PUBLISHED_OCF,
        Instant.parse("2026-11-02T08:30:00Z"));
  }
}
