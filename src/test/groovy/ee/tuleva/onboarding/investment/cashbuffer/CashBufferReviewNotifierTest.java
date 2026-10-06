package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.MISSING_PARAMETERS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_COMPLETE_MONTH_OF_FLOWS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRun;
import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.Reviewed;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashBufferReviewNotifierTest {

  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final String HEADER =
      "CASH BUFFER REVIEW 2026-09 — the recommended day-to-day operating buffer (recurring and"
          + " one-off payouts; PEVA, RAVA and PIK cycle outflows left out), not the fund's total"
          + " cash requirement. The job recommends only; investment_fund_limit is never changed"
          + " by it.";

  @Mock private OperationsNotificationService notificationService;
  @InjectMocks private CashBufferReviewNotifier notifier;

  @Test
  void staysSilentWhileNoLimitHasDriftedForTheConsecutiveRunsItTakes() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(review(TUK75, drift("-110300.00", true, 5), drift("-56000.00", true, 5))),
            new Reviewed(review(TUK00, drift("-100.00", false, 0), null))));

    verify(notificationService, never()).sendMessage(anyString(), any(), any());
  }

  @Test
  void aSoftLimitDriftSustainedAcrossConsecutiveRunsAsksForTheSoftLimitToBeReviewed() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(review(TUK75, drift("-110300.00", true, 6), drift("-100.00", false, 0))),
            new Reviewed(review(TUK00, drift("-100.00", false, 0), null))));

    verify(notificationService)
        .sendMessage(
            HEADER
                + "\nLIMIT DRIFTED FROM THE RECOMMENDATION — review the reserve"
                + "\n  TUK75 reserve_soft: recommended 20700.00 EUR, configured 131000.00 EUR since"
                + " 2026-01-01, apart by -110300.00 EUR (threshold 50000.00 EUR) for 6 consecutive"
                + " months — P95 monthly outflow 65700.00 less 0.1 × P20 monthly inflow 480000.00,"
                + " plus accrued fees 3000.00; window 2026-08..2026-09 (2 months)",
            INVESTMENT,
            INFO);
  }

  @Test
  void aHardLimitDriftSustainedAcrossConsecutiveRunsNamesTheSettlementHorizonItCovers() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(review(TUK75, drift("-100.00", false, 0), drift("-56000.00", true, 6)))));

    verify(notificationService)
        .sendMessage(
            HEADER
                + "\nLIMIT DRIFTED FROM THE RECOMMENDATION — review the reserve"
                + "\n  TUK75 reserve_hard: recommended 24000.00 EUR, configured 80000.00 EUR since"
                + " 2026-01-01, apart by -56000.00 EUR (threshold 50000.00 EUR) for 6 consecutive"
                + " months — P95 outflow over 4 business days 21000.00, plus accrued fees 3000.00;"
                + " window 2026-08..2026-09 (2 months)",
            INVESTMENT,
            INFO);
  }

  @Test
  void anUnrecognisedPayoutReasonInTheReviewMonthOrAFundThatCouldNotBeReviewedIsAnError() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(reviewWithAnUnrecognisedPayoutIn(SEPTEMBER)),
            new NotRun(
                TUK00, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_SETTLEMENT_HORIZON_DAYS]"),
            new NotRun(TUV100, NO_COMPLETE_MONTH_OF_FLOWS, "reviewMonth=2026-09")));

    verify(notificationService)
        .sendMessage(
            HEADER
                + "\nPAYOUT REASON NOT RECOGNISED — left out of the buffer until it is mapped in"
                + " RegistrarPayoutReason"
                + "\n  TUK75: 1 registrar payout(s), 5000.00 EUR booked in 2026-09"
                + "\nREVIEW COULD NOT RUN"
                + "\n  TUK00: missing investment_parameter"
                + " (parameters=[CASH_BUFFER_SETTLEMENT_HORIZON_DAYS])"
                + "\n  TUV100: the ledger holds no complete month of registrar flows yet"
                + " (reviewMonth=2026-09)",
            INVESTMENT,
            ERROR);
  }

  @Test
  void anUnrecognisedPayoutFromAMonthBeforeTheReviewMonthIsNotRaisedAgain() {
    notifier.notify(SEPTEMBER, List.of(new Reviewed(reviewWithAnUnrecognisedPayoutIn(AUGUST))));

    verify(notificationService, never()).sendMessage(anyString(), any(), any());
  }

  @Test
  void aNotificationThatCannotBeSentLeavesTheStoredReviewsStanding() {
    willThrow(new IllegalStateException("webhook down"))
        .given(notificationService)
        .sendMessage(anyString(), any(), any());

    assertThatCode(
            () ->
                notifier.notify(
                    SEPTEMBER,
                    List.of(
                        new NotRun(
                            TUK00,
                            MISSING_PARAMETERS,
                            "parameters=[CASH_BUFFER_SETTLEMENT_HORIZON_DAYS]"))))
        .doesNotThrowAnyException();
  }

  private static Drift drift(String divergence, boolean drifted, int consecutiveRuns) {
    return new Drift(amount(divergence), amount("50000.00"), drifted, consecutiveRuns, 6);
  }

  private static CashBufferReview review(
      TulevaFund fund, Drift softDrift, @Nullable Drift hardDrift) {
    return review(fund, softDrift, hardDrift, 0, 0);
  }

  private static CashBufferReview reviewWithAnUnrecognisedPayoutIn(YearMonth month) {
    var noDrift = drift("-100.00", false, 0);
    return month.equals(SEPTEMBER)
        ? review(TUK75, noDrift, null, 0, 1)
        : review(TUK75, noDrift, null, 1, 0);
  }

  private static CashBufferReview review(
      TulevaFund fund,
      Drift softDrift,
      @Nullable Drift hardDrift,
      int unrecognisedInAugust,
      int unrecognisedInSeptember) {
    return new CashBufferReview(
        fund,
        SEPTEMBER,
        LocalDate.of(2026, 10, 6),
        new FlowWindow(
            List.of(
                new MonthlyFlows(
                    AUGUST,
                    amount("800000.00"),
                    amount("12000.00"),
                    amount("60000.00"),
                    amount("0.00"),
                    unrecognisedOutflow(unrecognisedInAugust),
                    unrecognisedInAugust),
                new MonthlyFlows(
                    SEPTEMBER,
                    amount("1000000.00"),
                    amount("11000.00"),
                    amount("4000.00"),
                    amount("0.00"),
                    unrecognisedOutflow(unrecognisedInSeptember),
                    unrecognisedInSeptember))),
        new Recommendation(
            new BufferModel(
                new BigDecimal("0.9500000000"),
                new BigDecimal("0.2000000000"),
                new BigDecimal("0.1000000000"),
                4),
            amount("65700.00"),
            amount("480000.00"),
            amount("21000.00"),
            amount("3000.00"),
            amount("20700.00"),
            amount("24000.00")),
        new ConfiguredReserve(
            LocalDate.of(2026, 1, 1),
            amount("131000.00"),
            hardDrift == null ? null : amount("80000.00")),
        softDrift,
        hardDrift);
  }

  private static BigDecimal unrecognisedOutflow(int unrecognisedPayouts) {
    return unrecognisedPayouts == 0 ? amount("0.00") : amount("5000.00");
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }
}
