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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashBufferReviewNotifierTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final String HEADER =
      "CASH BUFFER REVIEW 2026-09 — the recommended day-to-day operating buffer (recurring and"
          + " one-off payouts; PEVA, RAVA and PIK cycle outflows left out), not the fund's total"
          + " cash requirement. The job recommends only; investment_fund_limit is never changed"
          + " by it.";

  @Mock private OperationsNotificationService notificationService;
  @InjectMocks private CashBufferReviewNotifier notifier;

  @Test
  void staysSilentWhileNoFundHasDriftedForTheConsecutiveRunsItTakes() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(
                review(TUK75, new Drift(amount("-60000.00"), amount("50000.00"), true, 1, 2), 0)),
            new Reviewed(
                review(TUK00, new Drift(amount("-100.00"), amount("50000.00"), false, 0, 2), 0))));

    verify(notificationService, never()).sendMessage(anyString(), any(), any());
  }

  @Test
  void aDriftSustainedAcrossConsecutiveRunsAsksForTheReserveToBeReviewed() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(
                review(TUK75, new Drift(amount("-86300.00"), amount("50000.00"), true, 2, 2), 0)),
            new Reviewed(
                review(TUK00, new Drift(amount("-100.00"), amount("50000.00"), false, 0, 2), 0))));

    verify(notificationService)
        .sendMessage(
            HEADER
                + "\nLIMIT DRIFTED FROM THE RECOMMENDATION — review the reserve"
                + "\n  TUK75: recommended 44700.00 EUR, reserve_soft 131000.00 EUR since"
                + " 2026-01-01, apart by -86300.00 EUR (threshold 50000.00 EUR) for 2 consecutive"
                + " months — P95 monthly outflow 65700.00 less 0.1 × P20 monthly inflow 480000.00,"
                + " plus floor 24000.00 and accrued fees 3000.00; window 2026-08..2026-09 (2"
                + " months)",
            INVESTMENT,
            INFO);
  }

  @Test
  void anUnrecognisedPayoutReasonOrAFundThatCouldNotBeReviewedIsAnError() {
    notifier.notify(
        SEPTEMBER,
        List.of(
            new Reviewed(
                review(TUK75, new Drift(amount("-100.00"), amount("50000.00"), false, 0, 2), 1)),
            new NotRun(TUK00, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"),
            new NotRun(TUV100, NO_COMPLETE_MONTH_OF_FLOWS, "reviewMonth=2026-09")));

    verify(notificationService)
        .sendMessage(
            HEADER
                + "\nPAYOUT REASON NOT RECOGNISED — left out of the buffer until it is mapped in"
                + " RegistrarPayoutReason"
                + "\n  TUK75: 1 registrar payout(s), 5000.00 EUR; window 2026-08..2026-09 (2"
                + " months)"
                + "\nREVIEW COULD NOT RUN"
                + "\n  TUK00: missing investment_parameter (parameters=[CASH_BUFFER_FLOOR])"
                + "\n  TUV100: the ledger holds no complete month of registrar flows yet"
                + " (reviewMonth=2026-09)",
            INVESTMENT,
            ERROR);
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
                        new NotRun(TUK00, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"))))
        .doesNotThrowAnyException();
  }

  private static CashBufferReview review(TulevaFund fund, Drift drift, int unrecognisedPayouts) {
    var unrecognisedOutflow = unrecognisedPayouts == 0 ? amount("0.00") : amount("5000.00");
    return new CashBufferReview(
        fund,
        SEPTEMBER,
        LocalDate.of(2026, 10, 6),
        new FlowWindow(
            List.of(
                new MonthlyFlows(
                    YearMonth.of(2026, 8),
                    amount("800000.00"),
                    amount("12000.00"),
                    amount("60000.00"),
                    amount("0.00"),
                    unrecognisedOutflow,
                    unrecognisedPayouts),
                new MonthlyFlows(
                    SEPTEMBER,
                    amount("1000000.00"),
                    amount("11000.00"),
                    amount("4000.00"),
                    amount("0.00"),
                    amount("0.00"),
                    0))),
        new Recommendation(
            new BufferModel(
                new BigDecimal("0.9500000000"),
                new BigDecimal("0.2000000000"),
                new BigDecimal("0.1000000000"),
                amount("24000.00")),
            amount("65700.00"),
            amount("480000.00"),
            amount("3000.00"),
            amount("44700.00")),
        new ConfiguredReserve(LocalDate.of(2026, 1, 1), amount("131000.00"), null),
        drift);
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }
}
