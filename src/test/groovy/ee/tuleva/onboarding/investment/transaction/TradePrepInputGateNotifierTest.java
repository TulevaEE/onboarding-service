package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR16;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradePrepInputGateNotifierTest {

  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final TradePrepInputGateNotifier notifier =
      new TradePrepInputGateNotifier(notificationService);

  private final List<InputGateFailure> failures =
      List.of(
          new MissingR16(
              YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9)));

  @Test
  void postsOneLinePerFailureUnderAHeaderNamingTheFundAndTheTradeDate() {
    notifier.notifyStopped(TUK00, LocalDate.of(2026, 11, 18), failures);

    verify(notificationService)
        .sendMessage(
            """
            🔴 TUK00 tehingute mustandit ei tehtud (2026-11-18). Käivita ettevalmistus uuesti, \
            kui sisend on korras:
            R16 2026-11 puudub (väljamaksed 2026-11-16, arvesse alates 2026-11-09): lae R16 \
            EPIS-ist""",
            INVESTMENT);
  }

  @Test
  void aSlackOutageDoesNotHideTheStopBehindAnotherError() {
    willThrow(new IllegalStateException("No webhook for slack channel INVESTMENT"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    assertThatCode(() -> notifier.notifyStopped(TUK00, LocalDate.of(2026, 11, 18), failures))
        .doesNotThrowAnyException();
  }
}
