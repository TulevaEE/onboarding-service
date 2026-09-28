package ee.tuleva.onboarding.investment.cashbuffer;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

sealed interface FundReviewOutcome {

  TulevaFund fund();

  record Reviewed(CashBufferReview review) implements FundReviewOutcome {

    @Override
    public TulevaFund fund() {
      return review.fund();
    }
  }

  record NotRun(TulevaFund fund, NotRunReason reason, String detail) implements FundReviewOutcome {}

  @Getter
  @RequiredArgsConstructor
  enum NotRunReason {
    MISSING_PARAMETERS("missing investment_parameter"),
    NO_RESERVE_CONFIGURED("no reserve_soft in investment_fund_limit"),
    NO_COMPLETE_MONTH_OF_FLOWS("the ledger holds no complete month of registrar flows yet"),
    NO_FEE_ACCRUALS("no investment_fee_accrual rows for the review month"),
    FAILED("failed, the cause is in the application log");

    private final String description;
  }
}
