package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckNotification.NOTHING_TO_REPORT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckNotification.SEND_FAILED;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckNotification.SENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.LEDGER_ACCRUAL_CONSISTENCY;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCheckNotifierTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 6, 4);
  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);

  @Mock private FeeCheckEventRepository eventRepository;
  @Mock private OperationsNotificationService notificationService;

  private FeeCheckNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new FeeCheckNotifier(eventRepository, notificationService);
  }

  @Test
  void aPersistingDailyFailureOnASecondDayIsSilent() {
    givenDailyHistory(FAIL, FAIL);

    assertThat(notifier.notify(List.of(dailyResult(FAIL)))).isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void aFreshDailyFailureAlertsOnce() {
    givenDailyHistory(FAIL, PASS);

    assertThat(notifier.notify(List.of(dailyResult(FAIL)))).isEqualTo(SENT);
    verify(notificationService).sendMessage(contains("LEDGER_ACCRUAL_CONSISTENCY"), eq(INVESTMENT));
  }

  @Test
  void aNewMonthFailingAfterThePreviousMonthAlreadyFailedStillAlerts() {
    givenMonthlyHistory(JUNE, FAIL, PASS);

    assertThat(notifier.notify(List.of(monthlyResult(JUNE, FAIL)))).isEqualTo(SENT);
    verify(notificationService).sendMessage(contains("2026-06-01"), eq(INVESTMENT));
  }

  @Test
  void monthlyBucketsDoNotSeeEachOthersHistory() {
    givenMonthlyHistory(MAY, FAIL, FAIL);

    assertThat(notifier.notify(List.of(monthlyResult(MAY, FAIL)))).isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void aResolvedFailureIsReportedAsCleared() {
    givenDailyHistory(PASS, FAIL);

    assertThat(notifier.notify(List.of(dailyResult(PASS)))).isEqualTo(SENT);
    verify(notificationService).sendMessage(contains("[CLEARED]"), eq(INVESTMENT));
  }

  @Test
  void goingBlindIsReportedSeparatelyFromADeviation() {
    givenDailyHistory(NOT_RUN, PASS);

    assertThat(notifier.notify(List.of(dailyResult(NOT_RUN)))).isEqualTo(SENT);
    verify(notificationService).sendMessage(contains("Could not check"), eq(INVESTMENT));
  }

  @Test
  void stayingBlindIsSilent() {
    givenDailyHistory(NOT_RUN, NOT_RUN);

    assertThat(notifier.notify(List.of(dailyResult(NOT_RUN)))).isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void aFailingSlackSendNeverBreaksTheCheck() {
    givenDailyHistory(FAIL, PASS);
    willThrow(new RuntimeException("slack is down"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThat(notifier.notify(List.of(dailyResult(FAIL)))).isEqualTo(SEND_FAILED);
  }

  private void givenDailyHistory(FeeCheckSeverity current, FeeCheckSeverity previous) {
    given(
            eventRepository
                .findTop2ByFundAndCheckTypeAndFeeScopeAndAlertFailedFalseAndFeeMonthIsNullOrderByCreatedAtDesc(
                    TUK75, LEDGER_ACCRUAL_CONSISTENCY, MANAGEMENT))
        .willReturn(List.of(event(current), event(previous)));
  }

  private void givenMonthlyHistory(
      LocalDate feeMonth, FeeCheckSeverity current, FeeCheckSeverity previous) {
    given(
            eventRepository
                .findTop2ByFundAndCheckTypeAndFeeScopeAndAlertFailedFalseAndFeeMonthOrderByCreatedAtDesc(
                    TUK75, LEDGER_ACCRUAL_CONSISTENCY, MANAGEMENT, feeMonth))
        .willReturn(List.of(event(current), event(previous)));
  }

  private FeeCheckEvent event(FeeCheckSeverity severity) {
    return FeeCheckEvent.builder().fund(TUK75).severity(severity).build();
  }

  private FeeCheckResult dailyResult(FeeCheckSeverity severity) {
    return new FeeCheckResult(TUK75, CHECK_DATE, null, List.of(finding(severity)));
  }

  private FeeCheckResult monthlyResult(LocalDate feeMonth, FeeCheckSeverity severity) {
    return new FeeCheckResult(TUK75, CHECK_DATE, feeMonth, List.of(finding(severity)));
  }

  private FeeCheckFinding finding(FeeCheckSeverity severity) {
    return new FeeCheckFinding(
        TUK75,
        LEDGER_ACCRUAL_CONSISTENCY,
        MANAGEMENT,
        severity,
        severity == PASS ? "" : severity + " detail",
        null,
        java.util.Map.of());
  }
}
