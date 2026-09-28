package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.FAILED;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.MISSING_PARAMETERS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_COMPLETE_MONTH_OF_FLOWS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_FEE_ACCRUALS;
import static ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRunReason.NO_RESERVE_CONFIGURED;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_FLOOR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRun;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashBufferReviewServiceTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final LocalDate REVIEWED_ON = LocalDate.of(2026, 10, 6);

  @Mock private CashBufferParameters parameters;
  @Mock private FundLimitRepository fundLimitRepository;
  @Mock private FlowWindowReader flowWindowReader;
  @Mock private ChargedFeeAccruals chargedFeeAccruals;
  @Mock private CashBufferReviewRepository repository;
  @Mock private CashBufferReviewNotifier notifier;
  @InjectMocks private CashBufferReviewService service;

  @Test
  void reviewsThePensionFundsOnlyAndReportsEveryOutcome() {
    given(parameters.missing(any(), any())).willReturn(List.of(CASH_BUFFER_FLOOR));

    var outcomes = service.reviewAllFunds(SEPTEMBER, REVIEWED_ON);

    assertThat(outcomes)
        .containsExactly(
            new NotRun(TUK75, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"),
            new NotRun(TUK00, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"),
            new NotRun(TUV100, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"));
    verify(notifier).notify(SEPTEMBER, outcomes);
  }

  @Test
  void aFundWithoutAReserveSoftInForceIsNotReviewed() {
    parametersPresent();
    given(fundLimitRepository.findLatestByFundAsOf(TUK75, REVIEWED_ON))
        .willReturn(Optional.of(limitWithoutReserve()));

    assertThat(service.reviewAllFunds(SEPTEMBER, REVIEWED_ON).getFirst())
        .isEqualTo(new NotRun(TUK75, NO_RESERVE_CONFIGURED, "asOf=2026-10-06"));
    verify(repository, never()).save(any());
  }

  @Test
  void aFundWhoseLedgerHoldsNoCompleteMonthYetIsNotReviewed() {
    parametersPresent();
    reserveInForce(TUK75);
    given(flowWindowReader.everyCompleteMonthThrough(TUK75, SEPTEMBER))
        .willReturn(Optional.empty());

    assertThat(service.reviewAllFunds(SEPTEMBER, REVIEWED_ON).getFirst())
        .isEqualTo(new NotRun(TUK75, NO_COMPLETE_MONTH_OF_FLOWS, "reviewMonth=2026-09"));
    verify(repository, never()).save(any());
  }

  @Test
  void aMonthTheFeeCalculationNeverAccruedIsNotReviewedRatherThanReadAsNoFees() {
    parametersPresent();
    reserveInForce(TUK75);
    given(flowWindowReader.everyCompleteMonthThrough(TUK75, SEPTEMBER))
        .willReturn(Optional.of(new FlowWindow(List.of())));
    given(chargedFeeAccruals.accruedDuring(TUK75, SEPTEMBER)).willReturn(Optional.empty());

    assertThat(service.reviewAllFunds(SEPTEMBER, REVIEWED_ON).getFirst())
        .isEqualTo(new NotRun(TUK75, NO_FEE_ACCRUALS, "feeMonth=2026-09"));
    verify(repository, never()).save(any());
  }

  @Test
  void oneFundFailingDoesNotStopTheOthersFromBeingReviewed() {
    given(parameters.missing(TUK75, REVIEWED_ON)).willThrow(new IllegalStateException("boom"));
    given(parameters.missing(TUK00, REVIEWED_ON)).willReturn(List.of(CASH_BUFFER_FLOOR));
    given(parameters.missing(TUV100, REVIEWED_ON)).willReturn(List.of(CASH_BUFFER_FLOOR));

    assertThat(service.reviewAllFunds(SEPTEMBER, REVIEWED_ON))
        .containsExactly(
            new NotRun(TUK75, FAILED, "exception=IllegalStateException"),
            new NotRun(TUK00, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"),
            new NotRun(TUV100, MISSING_PARAMETERS, "parameters=[CASH_BUFFER_FLOOR]"));
  }

  private void parametersPresent() {
    given(parameters.missing(any(), any())).willReturn(List.of());
  }

  private void reserveInForce(TulevaFund fund) {
    given(fundLimitRepository.findLatestByFundAsOf(fund, REVIEWED_ON))
        .willReturn(
            Optional.of(
                FundLimit.builder()
                    .fund(fund)
                    .effectiveDate(LocalDate.of(2026, 1, 1))
                    .reserveSoft(new BigDecimal("131000.00"))
                    .reserveHard(new BigDecimal("77000.00"))
                    .build()));
  }

  private static FundLimit limitWithoutReserve() {
    return FundLimit.builder()
        .fund(TUK75)
        .effectiveDate(LocalDate.of(2026, 1, 1))
        .minTransaction(new BigDecimal("1000.00"))
        .build();
  }
}
