package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.NO_PUBLISHED_NAV_CALCULATION;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfNotifierTest {

  private static final YearMonth MONTH = YearMonth.of(2026, 4);

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private OcfNotifier notifier;

  @Test
  void aRunWhereEveryFundFailedIsReportedAsProducingNothing() {
    notifier.notifyRun(MONTH, List.of(failed(TUK75), failed(TUK00)));

    then(notificationService)
        .should()
        .sendMessage(contains("DID NOT PRODUCE A SINGLE FIGURE"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aRunWhereEveryFundFailedNamesEachFundAndItsReason() {
    notifier.notifyRun(MONTH, List.of(failed(TUK75), failed(TUK00)));

    then(notificationService)
        .should()
        .sendMessage(contains("TUK75 2026-04"), eq(INVESTMENT), eq(ERROR));
    then(notificationService)
        .should()
        .sendMessage(contains("no rate for XX0000000001"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aRunWhereOneFundFailedIsReportedAsPartial() {
    notifier.notifyRun(MONTH, List.of(failed(TUK75), computed(TUK00, "0.0034")));

    then(notificationService)
        .should()
        .sendMessage(
            contains("RAN ONLY IN PART: month=2026-04, 1 of 2 funds failed"),
            eq(INVESTMENT),
            eq(ERROR));
  }

  @Test
  void aRunWhereNothingThrewButAFundHasGapsIsReportedAsIncomplete() {
    notifier.notifyRun(
        MONTH,
        List.of(
            computed(TUK75, "0.0034"), incomplete(TUK00, "0.0021", NO_PUBLISHED_NAV_CALCULATION)));

    then(notificationService)
        .should()
        .sendMessage(contains("WROTE AN INCOMPLETE FIGURE"), eq(INVESTMENT), eq(ERROR));
    then(notificationService)
        .should()
        .sendMessage(contains("NO_PUBLISHED_NAV_CALCULATION"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aCleanRunReportsEachFundsTotalOcf() {
    notifier.notifyRun(MONTH, List.of(computed(TUK75, "0.0034"), computed(TUK00, "0.0021")));

    then(notificationService)
        .should()
        .sendMessage(contains("OCF RUN COMPLETE: month=2026-04"), eq(INVESTMENT), eq(INFO));
    then(notificationService)
        .should()
        .sendMessage(contains("TUK75 2026-04: 0.34%"), eq(INVESTMENT), eq(INFO));
  }

  @Test
  void aBackfillIsOneMessageForTheWholeRunRatherThanOnePerMonth() {
    notifier.notifyBackfill(
        3, List.of(computed(TUK75, "0.0034"), failed(TUK00), computed(TUK75, "0.0031")));

    then(notificationService)
        .should()
        .sendMessage(
            contains("OCF BACKFILL RAN ONLY IN PART: monthsBack=3"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aFailureDoesNotSuppressTheMustNotBePublishedWarningForAnIncompleteFund() {
    notifier.notifyRun(
        MONTH, List.of(failed(TUK75), incomplete(TUK00, "0.0021", NO_PUBLISHED_NAV_CALCULATION)));

    then(notificationService)
        .should()
        .sendMessage(contains("must not be published"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aBackfillSpanningMonthsCountsFundMonthsRatherThanFunds() {
    notifier.notifyBackfill(
        2,
        List.of(
            failed(TUK75),
            computed(TUK00, "0.0021"),
            OcfRunOutcome.computed(
                TUK75, MONTH.minusMonths(1), snapshot(TUK75, "0.0034", true), List.of())));

    then(notificationService)
        .should()
        .sendMessage(contains("1 of 3 fund-months failed"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aSingleMonthRunCountsFunds() {
    notifier.notifyRun(MONTH, List.of(failed(TUK75), computed(TUK00, "0.0021")));

    then(notificationService)
        .should()
        .sendMessage(contains("1 of 2 funds failed"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void aNotificationThatCannotBeSentDoesNotTakeTheRunDown() {
    willThrow(new RuntimeException("slack is down"))
        .given(notificationService)
        .sendMessage(any(), any(), any());

    assertThatCode(() -> notifier.notifyRun(MONTH, List.of(computed(TUK75, "0.0034"))))
        .doesNotThrowAnyException();
  }

  @Test
  void anEmptyRunSendsNothingRatherThanClaimingEveryFundFailed() {
    notifier.notifyRun(MONTH, List.of());

    then(notificationService).should(never()).sendMessage(any(), any(), any());
  }

  private static OcfRunOutcome failed(TulevaFund fund) {
    return OcfRunOutcome.failed(fund, MONTH, "no rate for XX0000000001");
  }

  private static OcfRunOutcome computed(TulevaFund fund, String totalOcf) {
    return OcfRunOutcome.computed(fund, MONTH, snapshot(fund, totalOcf, true), List.of());
  }

  private static OcfRunOutcome incomplete(TulevaFund fund, String totalOcf, OcfGap gap) {
    return OcfRunOutcome.computed(fund, MONTH, snapshot(fund, totalOcf, false), List.of(gap));
  }

  private static OcfSnapshot snapshot(TulevaFund fund, String totalOcf, boolean complete) {
    return OcfSnapshot.computed(
        fund.getCode(),
        MONTH.atDay(1),
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        RebateBasis.NET,
        ZERO,
        new BigDecimal(totalOcf),
        complete,
        null,
        emptyAudit());
  }

  private static OcfAudit emptyAudit() {
    return new OcfAudit(null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
