package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckNotification.NOTHING_TO_REPORT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckNotification.SEND_FAILED;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckNotification.SENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.INFO;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.LEDGER_ACCRUAL_CONSISTENCY;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
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
  void aPersistingFailureThatGrewAlertsAgainSoANewBadDayIsNotSwallowed() {
    givenDailyHistory(event(FAIL, "1000"), event(FAIL, "1000"));

    assertThat(notifier.notify(List.of(dailyResult(FAIL, "7500")))).isEqualTo(SENT);
  }

  @Test
  void aPersistingFailureThatDidNotMoveStaysSilent() {
    givenDailyHistory(event(FAIL, "1000"), event(FAIL, "1000"));

    assertThat(notifier.notify(List.of(dailyResult(FAIL, "1000")))).isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void anUnchangedDeviationIsComparedByValueNotByScale() {
    givenDailyHistory(event(FAIL, "1000.00"), event(FAIL, "1000.00"));

    assertThat(notifier.notify(List.of(dailyResult(FAIL, "1000.0")))).isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  // deviation_amount is nullable, so a check that has never carried an amount reads back as null
  // and the previous state has to survive it.
  @Test
  void aFailureThatAcquiresADeviationWhereThereWasNoneAlerts() {
    givenDailyHistory(event(FAIL, null), event(FAIL, null));

    assertThat(notifier.notify(List.of(dailyResult(FAIL, "500")))).isEqualTo(SENT);
  }

  @Test
  void aFailureThatAcquiresAFindingCarryingNoDeviationAlerts() {
    var divergentDay = finding(FAIL, new BigDecimal("1000"), "2026-06-01 divergence");
    givenDailyHistory(event(FAIL, "1000", divergentDay), event(FAIL, "1000", divergentDay));

    var missingSettlementTransaction = finding(FAIL, null, "settlementTransactionCount=1/2");

    assertThat(notifier.notify(List.of(dailyResult(divergentDay, missingSettlementTransaction))))
        .isEqualTo(SENT);
  }

  // The finding messages of a re-alert at an unchanged severity are the ones the operator read on
  // the run they already dismissed, so the message has to carry the newly found one.
  @Test
  void aReAlertAtAnUnchangedSeverityNamesWhatWasNewlyFound() {
    var divergentDay = findingSaying("a divergent day nobody has fixed", "2026-06-01 divergence");
    givenDailyHistory(event(FAIL, "0", divergentDay), event(FAIL, "0", divergentDay));
    var duplicateSettlement =
        findingSaying("a second settlement transaction appeared", "settlementTransactionCount=1/2");

    assertThat(notifier.notify(List.of(dailyResult(divergentDay, duplicateSettlement))))
        .isEqualTo(SENT);
    verify(notificationService)
        .sendMessage(contains("a second settlement transaction appeared"), eq(INVESTMENT));
  }

  @Test
  void aReAlertNamesTheFingerprintEntryItGained() {
    var divergentDay = findingSaying("a divergent day nobody has fixed", "2026-06-01 divergence");
    givenDailyHistory(event(FAIL, "0", divergentDay), event(FAIL, "0", divergentDay));
    var duplicateSettlement =
        findingSaying("a second settlement transaction appeared", "settlementTransactionCount=1/2");

    notifier.notify(List.of(dailyResult(divergentDay, duplicateSettlement)));

    verify(notificationService)
        .sendMessage(contains("settlementTransactionCount=1/2"), eq(INVESTMENT));
  }

  // A fixed fee month has no rolling window and no fund-wide anchor, so nothing but the money
  // itself can make its total fall - a settlement shortfall that improved still has to speak.
  @Test
  void aMonthlyTotalThatFellIsStillReported() {
    var shortfall = finding(FAIL, new BigDecimal("500"), "settledAmount");
    givenMonthlyHistory(JUNE, event(FAIL, "500", shortfall), event(FAIL, "500", shortfall));

    var improved = finding(FAIL, new BigDecimal("100"), "settledAmount");

    assertThat(notifier.notify(List.of(monthlyResult(JUNE, improved)))).isEqualTo(SENT);
  }

  @Test
  void aMonthlyTotalThatDidNotMoveStaysSilent() {
    var shortfall = finding(FAIL, new BigDecimal("500"), "settledAmount");
    givenMonthlyHistory(JUNE, event(FAIL, "500", shortfall), event(FAIL, "500", shortfall));

    assertThat(notifier.notify(List.of(monthlyResult(JUNE, shortfall))))
        .isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  // Every row written before this mechanism existed carries no fingerprint at all. Reading those as
  // "reported nothing" would make the first run after the deploy announce every standing check
  // across every fund at once.
  @Test
  void aPreviousRowFromBeforeFingerprintsExistedDoesNotMakeAStandingCheckSpeakAgain() {
    givenDailyHistory(eventPredatingFingerprints(FAIL), eventPredatingFingerprints(FAIL));

    var standing = finding(FAIL, new BigDecimal("1000"), "2026-06-01 divergence");

    assertThat(notifier.notify(List.of(dailyResult(standing)))).isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void aPreviousRowFromBeforeFingerprintsExistedStillReportsASeverityChange() {
    givenDailyHistory(eventPredatingFingerprints(FAIL), eventPredatingFingerprints(PASS));

    assertThat(notifier.notify(List.of(dailyResult(FAIL)))).isEqualTo(SENT);
  }

  @Test
  void aTotalThatFellOnlyBecauseTheWindowRolledStaysSilent() {
    var stillInTheWindow = finding(FAIL, new BigDecimal("1000"), "2026-06-01 divergence");
    var rolledOutOfTheWindow = finding(FAIL, new BigDecimal("500"), "2026-05-01 divergence");
    givenDailyHistory(
        event(FAIL, "1500", stillInTheWindow, rolledOutOfTheWindow),
        event(FAIL, "1500", stillInTheWindow, rolledOutOfTheWindow));

    assertThat(notifier.notify(List.of(dailyResult(stillInTheWindow))))
        .isEqualTo(NOTHING_TO_REPORT);
    verifyNoInteractions(notificationService);
  }

  @Test
  void aNoteWhoseOldestDayRolledOutOfTheWindowIsNotReportedAgain() {
    var stillInTheWindow = finding(INFO, new BigDecimal("200"), "2026-06-02 re-sent");
    var rolledOutOfTheWindow = finding(INFO, new BigDecimal("300"), "2026-05-02 re-sent");
    givenDailyHistory(
        event(INFO, "500", stillInTheWindow, rolledOutOfTheWindow),
        event(INFO, "500", stillInTheWindow, rolledOutOfTheWindow));

    assertThat(notifier.notify(List.of(dailyResult(stillInTheWindow))))
        .isEqualTo(NOTHING_TO_REPORT);
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
    givenDailyHistory(event(current), event(previous));
  }

  private void givenDailyHistory(FeeCheckEvent current, FeeCheckEvent previous) {
    given(
            eventRepository.findLatestDelivered(
                eq(TUK75), eq(LEDGER_ACCRUAL_CONSISTENCY), eq(MANAGEMENT), any()))
        .willReturn(List.of(current, previous));
  }

  private FeeCheckEvent event(
      FeeCheckSeverity severity, @Nullable String deviation, FeeCheckFinding... findings) {
    return FeeCheckEvent.builder()
        .fund(TUK75)
        .severity(severity)
        .deviationAmount(deviation == null ? null : new BigDecimal(deviation))
        .result(Map.of(FeeCheckEvent.FINGERPRINT, FeeCheckFinding.fingerprint(List.of(findings))))
        .build();
  }

  private FeeCheckResult dailyResult(FeeCheckSeverity severity, String deviation) {
    return new FeeCheckResult(
        TUK75, CHECK_DATE, null, List.of(finding(severity, new BigDecimal(deviation))));
  }

  private FeeCheckResult dailyResult(FeeCheckFinding... findings) {
    return new FeeCheckResult(TUK75, CHECK_DATE, null, List.of(findings));
  }

  private void givenMonthlyHistory(
      LocalDate feeMonth, FeeCheckSeverity current, FeeCheckSeverity previous) {
    givenMonthlyHistory(feeMonth, event(current), event(previous));
  }

  private void givenMonthlyHistory(
      LocalDate feeMonth, FeeCheckEvent current, FeeCheckEvent previous) {
    given(
            eventRepository.findLatestDeliveredForFeeMonth(
                eq(TUK75), eq(LEDGER_ACCRUAL_CONSISTENCY), eq(MANAGEMENT), eq(feeMonth), any()))
        .willReturn(List.of(current, previous));
  }

  private FeeCheckEvent event(FeeCheckSeverity severity) {
    return event(severity, null);
  }

  private FeeCheckEvent eventPredatingFingerprints(FeeCheckSeverity severity) {
    return FeeCheckEvent.builder().fund(TUK75).severity(severity).build();
  }

  private FeeCheckResult dailyResult(FeeCheckSeverity severity) {
    return new FeeCheckResult(TUK75, CHECK_DATE, null, List.of(finding(severity)));
  }

  private FeeCheckResult monthlyResult(LocalDate feeMonth, FeeCheckSeverity severity) {
    return monthlyResult(feeMonth, finding(severity));
  }

  private FeeCheckResult monthlyResult(LocalDate feeMonth, FeeCheckFinding... findings) {
    return new FeeCheckResult(TUK75, CHECK_DATE, feeMonth, List.of(findings));
  }

  private FeeCheckFinding finding(FeeCheckSeverity severity) {
    return finding(severity, null);
  }

  private FeeCheckFinding findingSaying(String message, String... identifiers) {
    return new FeeCheckFinding(
        TUK75,
        LEDGER_ACCRUAL_CONSISTENCY,
        MANAGEMENT,
        FAIL,
        message,
        null,
        List.of(identifiers),
        Map.of());
  }

  private FeeCheckFinding finding(
      FeeCheckSeverity severity, @Nullable BigDecimal deviation, String... identifiers) {
    return new FeeCheckFinding(
        TUK75,
        LEDGER_ACCRUAL_CONSISTENCY,
        MANAGEMENT,
        severity,
        severity == PASS ? "" : severity + " detail",
        deviation,
        List.of(identifiers),
        Map.of());
  }
}
