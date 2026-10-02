package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.NO_PUBLISHED_NAV_CALCULATION;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED_NET;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.NONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.fees.ocf.OcfRunOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.ocf.OcfRunOutcome.Failed;
import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.investment.fees.rate.RateBasis;
import ee.tuleva.onboarding.investment.fees.rate.RebateKind;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;
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
  void aRunWhereEveryFundFailedIsReportedAsProducingNothingAndNamesEachFundsReason() {
    notifier.notifyRun(MONTH, List.of(failed(TUK75), failed(TUK00)), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            🛑 OCF RUN DID NOT PRODUCE A SINGLE FIGURE: month=2026-04
              Every one of the 2 funds failed, so no OCF was written for this period and the last
              figure on this channel is not this period's. Rerun it once the cause is fixed.
              🛑 TUK75 2026-04: no rate for XX0000000001
              🛑 TUK00 2026-04: no rate for XX0000000001""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void aRunWhereOneFundFailedIsReportedAsPartialAndCountsFunds() {
    notifier.notifyRun(MONTH, List.of(failed(TUK75), computed(TUK00, "0.0034")), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF RUN RAN ONLY IN PART: month=2026-04, 1 of 2 funds failed
              Nothing here says what their OCF is this period. The rest were written.
              🛑 TUK75 2026-04: no rate for XX0000000001
              ✅ TUK00 2026-04: 0.34%""",
            INVESTMENT, ERROR);
  }

  @Test
  void aRunWhereNothingThrewButAFundHasGapsIsReportedAsIncomplete() {
    notifier.notifyRun(
        MONTH,
        List.of(
            computed(TUK75, "0.0034"), incomplete(TUK00, "0.0021", NO_PUBLISHED_NAV_CALCULATION)),
        List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF RUN WROTE AN INCOMPLETE FIGURE: month=2026-04, 1 of 2 funds has gaps
              A component resolved to zero instead of failing, so that total is understated and
              must not be published until it is complete.
              ✅ TUK75 2026-04: 0.34%
              ⚠️ TUK00 2026-04: 0.21%, incomplete — NO_PUBLISHED_NAV_CALCULATION""",
            INVESTMENT, ERROR);
  }

  @Test
  void anIncompleteFundNamesEveryGapItHas() {
    notifier.notifyRun(
        MONTH,
        List.of(
            incomplete(
                TUK75,
                "0.0021",
                NO_PUBLISHED_NAV_CALCULATION,
                TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM)),
        List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF RUN WROTE AN INCOMPLETE FIGURE: month=2026-04, 1 of 1 funds has gaps
              A component resolved to zero instead of failing, so that total is understated and
              must not be published until it is complete.
              ⚠️ TUK75 2026-04: 0.21%, incomplete — NO_PUBLISHED_NAV_CALCULATION, \
            TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM""",
            INVESTMENT, ERROR);
  }

  @Test
  void aRunWhereSeveralFundsHaveGapsSpeaksOfThemInThePlural() {
    notifier.notifyRun(
        MONTH,
        List.of(
            incomplete(TUK75, "0.0034", NO_PUBLISHED_NAV_CALCULATION),
            incomplete(TUK00, "0.0021", NO_PUBLISHED_NAV_CALCULATION)),
        List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF RUN WROTE AN INCOMPLETE FIGURE: month=2026-04, 2 of 2 funds have gaps
              A component resolved to zero instead of failing, so those totals are understated and
              must not be published until they are complete.
              ⚠️ TUK75 2026-04: 0.34%, incomplete — NO_PUBLISHED_NAV_CALCULATION
              ⚠️ TUK00 2026-04: 0.21%, incomplete — NO_PUBLISHED_NAV_CALCULATION""",
            INVESTMENT, ERROR);
  }

  @Test
  void aCleanRunReportsEachFundsTotalOcfAsInfo() {
    notifier.notifyRun(
        MONTH, List.of(computed(TUK75, "0.0034"), computed(TUK00, "0.0021")), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ✅ OCF RUN COMPLETE: month=2026-04
              ✅ TUK75 2026-04: 0.34%
              ✅ TUK00 2026-04: 0.21%""",
            INVESTMENT, INFO);
  }

  @Test
  void aFailureDoesNotSuppressTheMustNotBePublishedWarningForAnIncompleteFund() {
    notifier.notifyRun(
        MONTH,
        List.of(failed(TUK75), incomplete(TUK00, "0.0021", NO_PUBLISHED_NAV_CALCULATION)),
        List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF RUN RAN ONLY IN PART: month=2026-04, 1 of 2 funds failed
              Nothing here says what their OCF is this period. The rest were written.
            ⚠️ 1 of the written ones has gaps: a component resolved to zero instead of failing,
              so that total is understated and must not be published until it is complete.
              🛑 TUK75 2026-04: no rate for XX0000000001
              ⚠️ TUK00 2026-04: 0.21%, incomplete — NO_PUBLISHED_NAV_CALCULATION""",
            INVESTMENT, ERROR);
  }

  @Test
  void aFailureBesideSeveralIncompleteFundsWarnsAboutThemInThePlural() {
    notifier.notifyRun(
        MONTH,
        List.of(
            failed(TUK75),
            incomplete(TUK00, "0.0021", NO_PUBLISHED_NAV_CALCULATION),
            incomplete(TUV100, "0.0040", NO_PUBLISHED_NAV_CALCULATION)),
        List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF RUN RAN ONLY IN PART: month=2026-04, 1 of 3 funds failed
              Nothing here says what their OCF is this period. The rest were written.
            ⚠️ 2 of the written ones have gaps: a component resolved to zero instead of failing,
              so those totals are understated and must not be published until they are complete.
              🛑 TUK75 2026-04: no rate for XX0000000001
              ⚠️ TUK00 2026-04: 0.21%, incomplete — NO_PUBLISHED_NAV_CALCULATION
              ⚠️ TUV100 2026-04: 0.40%, incomplete — NO_PUBLISHED_NAV_CALCULATION""",
            INVESTMENT, ERROR);
  }

  @Test
  void aBackfillIsOneMessageForTheWholeRunCountedInFundMonths() {
    notifier.notifyBackfill(
        2,
        List.of(
            failed(TUK75),
            computed(TUK00, "0.0021"),
            computed(TUK75, MONTH.minusMonths(1), "0.0034")));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ OCF BACKFILL RAN ONLY IN PART: monthsBack=2, 1 of 3 fund-months failed
              Nothing here says what their OCF is this period. The rest were written.
              🛑 TUK75 2026-04: no rate for XX0000000001
              ✅ TUK00 2026-04: 0.21%
              ✅ TUK75 2026-03: 0.34%""",
            INVESTMENT, ERROR);
  }

  @Test
  void aNotificationThatCannotBeSentDoesNotTakeTheRunDown() {
    willThrow(new RuntimeException("slack is down"))
        .given(notificationService)
        .sendMessage(any(), any(), any());

    assertThatCode(() -> notifier.notifyRun(MONTH, List.of(computed(TUK75, "0.0034")), List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void anEmptyRunSendsNothingRatherThanClaimingEveryFundFailed() {
    notifier.notifyRun(MONTH, List.of(), List.of());

    then(notificationService).should(never()).sendMessage(any(), any(), any());
  }

  private static OcfRunOutcome failed(TulevaFund fund) {
    return new Failed(fund, MONTH, "no rate for XX0000000001");
  }

  private static OcfRunOutcome computed(TulevaFund fund, String totalOcf) {
    return computed(fund, MONTH, totalOcf);
  }

  private static OcfRunOutcome computed(TulevaFund fund, YearMonth month, String totalOcf) {
    return new Computed(fund, month, snapshot(fund, month, totalOcf, true), List.of());
  }

  private static OcfRunOutcome incomplete(TulevaFund fund, String totalOcf, OcfGap... gaps) {
    return new Computed(fund, MONTH, snapshot(fund, MONTH, totalOcf, false), List.of(gaps));
  }

  private static OcfSnapshot snapshot(
      TulevaFund fund, YearMonth month, String totalOcf, boolean complete) {
    return OcfSnapshot.computed(
        fund.getCode(),
        month.atDay(1),
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

  @Test
  void aRateThatFellBackToThePublishedOcfIsNamedWithItsReasonAndRaisesTheRunToAnError() {
    notifier.notifyRun(
        MONTH,
        List.of(computed(TUK75, "0.0034")),
        List.of(
            rate(
                "ZZ0000000001",
                "0.00070000",
                "0.00070000",
                PUBLISHED_FALLBACK,
                FIXED_NET,
                "no volume"),
            rate("ZZ0000000002", "0.00200000", "0.00150000", AGREEMENT, FIXED, null)));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ✅ OCF RUN COMPLETE: month=2026-04
              ✅ TUK75 2026-04: 0.34%
            Instrument rates: 1 from agreements, 1 fell back to the published OCF
              ⚠️ ZZ0000000001 fell back to the published OCF: no volume""",
            INVESTMENT, ERROR);
  }

  @Test
  void anAgreedNetAboveThePublishedOcfIsNamedSoTheAgreementIsChecked() {
    notifier.notifyRun(
        MONTH,
        List.of(computed(TUK75, "0.0034")),
        List.of(rate("ZZ0000000001", "0.00070000", "0.00090000", AGREEMENT, FIXED_NET, null)));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ✅ OCF RUN COMPLETE: month=2026-04
              ✅ TUK75 2026-04: 0.34%
            Instrument rates: 1 from agreements, 0 fell back to the published OCF
              ⚠️ ZZ0000000001 agreed net OCF 0.090% is above the published 0.070%; the fund bears the agreed net, check the agreement""",
            INVESTMENT, INFO);
  }

  @Test
  void ratesAppliedFromTheirAgreementsAreCountedNotListed() {
    notifier.notifyRun(
        MONTH,
        List.of(computed(TUK75, "0.0034")),
        List.of(
            rate("ZZ0000000002", "0.00200000", "0.00150000", AGREEMENT, FIXED, null),
            rate("ZZ0000000003", "0.00070000", "0.00110000", AGREEMENT, NONE, null)));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ✅ OCF RUN COMPLETE: month=2026-04
              ✅ TUK75 2026-04: 0.34%
            Instrument rates: 2 from agreements, 0 fell back to the published OCF""",
            INVESTMENT, INFO);
  }

  private static InstrumentRate rate(
      String isin,
      String publishedOcf,
      String netOcf,
      RateBasis rateBasis,
      RebateKind rebateKind,
      @Nullable String fallbackReason) {
    return new InstrumentRate(
        1L,
        isin,
        MONTH,
        new BigDecimal(publishedOcf),
        new BigDecimal(netOcf),
        rateBasis,
        fallbackReason,
        rebateKind);
  }
}
