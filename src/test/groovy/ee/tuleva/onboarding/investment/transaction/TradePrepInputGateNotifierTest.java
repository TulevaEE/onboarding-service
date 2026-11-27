package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.HoldingMismatch;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPositionReport;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR16;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR45;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.StalePositionReport;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradePrepInputGateNotifierTest {

  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 12, 22);

  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final TradePrepInputGateNotifier notifier =
      new TradePrepInputGateNotifier(notificationService);

  private final MissingR16 missingR16 =
      new MissingR16(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9));
  private final MissingR45 missingR45 = new MissingR45(TRADE_DATE, LocalDate.of(2026, 12, 21));

  @Test
  void oneStoppedFundGetsItsOwnHeaderAndALinePerFailure() {
    notifier.notifyStopped(
        List.of(new StoppedByInputGates(TUK00, LocalDate.of(2026, 11, 18), List.of(missingR16))));

    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK00 tehingute mustandit ei tehtud (2026-11-18). Käivita ettevalmistus uuesti, kui sisend on korras:
            R16 2026-11 puudub (väljamaksed 2026-11-16, arvesse alates 2026-11-09): lae R16 EPIS-ist\
            """,
            INVESTMENT);
  }

  @Test
  void stoppedFundsShareOneMessageWithEachLineOnceAndNamedOnlyWhereNotEveryFundHasIt() {
    notifier.notifyStopped(
        List.of(
            new StoppedByInputGates(
                TUK75,
                TRADE_DATE,
                List.of(
                    missingR45,
                    new MissingPevaRavaReports(
                        List.of(R17_PEVA, R21_RAVA), LocalDate.of(2027, 1, 4), TRADE_DATE))),
            new StoppedByInputGates(TUK00, TRADE_DATE, List.of(missingR45)),
            new StoppedByInputGates(TUV100, TRADE_DATE, List.of(missingR45))));

    verify(notificationService)
        .sendMessage(
            """
            🔴 Tehingute mustandit ei tehtud (2026-12-22): TUK75, TUK00, TUV100. Käivita ettevalmistus uuesti, kui sisend on korras:
            R45 2026-12-22 puudub (viimane 2026-12-21): lae R45 EPIS-ist
            TUK75: R17 ja R21 puuduvad (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-22): lae EPIS-ist\
            """,
            INVESTMENT);
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void aBatchWhereTheR45StopsThePensionFundsAndAHoldingMismatchStopsTheSavingsFund() {
    var r45 = new MissingR45(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17));
    notifier.notifyStopped(
        List.of(
            new StoppedByInputGates(TUK75, LocalDate.of(2026, 11, 18), List.of(r45)),
            new StoppedByInputGates(TUK00, LocalDate.of(2026, 11, 18), List.of(r45)),
            new StoppedByInputGates(TUV100, LocalDate.of(2026, 11, 18), List.of(r45)),
            new StoppedByInputGates(
                TKF100,
                LocalDate.of(2026, 11, 18),
                List.of(
                    new HoldingMismatch(
                        "IE00BFG1TM61",
                        LocalDate.of(2026, 11, 17),
                        new BigDecimal("1000"),
                        new BigDecimal("990"))))));

    verify(notificationService)
        .sendMessage(
            """
            🔴 Tehingute mustandit ei tehtud (2026-11-18): TUK75, TUK00, TUV100, TKF100. Käivita ettevalmistus uuesti, kui sisend on korras:
            TUK75, TUK00, TUV100: R45 2026-11-18 puudub (viimane 2026-11-17): lae R45 EPIS-ist
            TKF100: IE00BFG1TM61: registris 1000, SEB raportis 990 (2026-11-17): kontrolli registri tehinguid\
            """,
            INVESTMENT);
  }

  @Test
  void aLineSomeOfTheStoppedFundsShareNamesThoseFunds() {
    var stale = new StalePositionReport(LocalDate.of(2026, 12, 18), LocalDate.of(2026, 12, 21));
    notifier.notifyStopped(
        List.of(
            new StoppedByInputGates(TUK75, TRADE_DATE, List.of(stale)),
            new StoppedByInputGates(TUK00, TRADE_DATE, List.of(stale)),
            new StoppedByInputGates(
                TKF100,
                TRADE_DATE,
                List.of(new MissingPositionReport(LocalDate.of(2026, 12, 21))))));

    verify(notificationService)
        .sendMessage(
            """
            🔴 Tehingute mustandit ei tehtud (2026-12-22): TUK75, TUK00, TKF100. Käivita ettevalmistus uuesti, kui sisend on korras:
            TUK75, TUK00: SEB positsiooniraport on 2026-12-18, vaja 2026-12-21: küsi 2026-12-21 raport SEB-lt
            TKF100: SEB positsiooniraport puudub, vaja 2026-12-21: küsi 2026-12-21 raport SEB-lt\
            """,
            INVESTMENT);
  }

  @Test
  void stopsForDifferentTradeDatesGetAMessageEach() {
    notifier.notifyStopped(
        List.of(
            new StoppedByInputGates(TUK00, LocalDate.of(2026, 11, 18), List.of(missingR16)),
            new StoppedByInputGates(TUK75, TRADE_DATE, List.of(missingR45))));

    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK00 tehingute mustandit ei tehtud (2026-11-18). Käivita ettevalmistus uuesti, kui sisend on korras:
            R16 2026-11 puudub (väljamaksed 2026-11-16, arvesse alates 2026-11-09): lae R16 EPIS-ist\
            """,
            INVESTMENT);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK75 tehingute mustandit ei tehtud (2026-12-22). Käivita ettevalmistus uuesti, kui sisend on korras:
            R45 2026-12-22 puudub (viimane 2026-12-21): lae R45 EPIS-ist\
            """,
            INVESTMENT);
  }

  @Test
  void aRunWithoutStopsPostsNothing() {
    notifier.notifyStopped(List.of());

    verifyNoInteractions(notificationService);
  }

  @Test
  void aSlackOutageDoesNotHideTheStopBehindAnotherError() {
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    assertThatCode(
            () ->
                notifier.notifyStopped(
                    List.of(
                        new StoppedByInputGates(
                            TUK00, LocalDate.of(2026, 11, 18), List.of(missingR16)))))
        .doesNotThrowAnyException();
  }
}
