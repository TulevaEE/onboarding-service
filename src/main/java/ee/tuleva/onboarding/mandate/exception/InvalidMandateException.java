package ee.tuleva.onboarding.mandate.exception;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class InvalidMandateException extends ErrorsResponseException {

  public InvalidMandateException(ErrorsResponse errorsResponse) {
    super(errorsResponse);
  }

  public static InvalidMandateException sourceAmountExceeded() {
    return new InvalidMandateException(
        ErrorsResponse.ofSingleError(
            "invalid.mandate.source.amount.exceeded", "Source amount exceeded."));
  }

  public static InvalidMandateException sameSourceAndTargetTransferPresent() {
    return new InvalidMandateException(
        ErrorsResponse.ofSingleError(
            "invalid.mandate.same.source.and.target.transfer.present",
            "Same source and target transfer present."));
  }

  public static InvalidMandateException futureContributionsToSameFund() {
    return new InvalidMandateException(
        ErrorsResponse.ofSingleError(
            "invalid.mandate.future.contributions.to.same.fund",
            "Future contributions cannot be made to the same fund."));
  }

  public static InvalidMandateException rejoinNotPending() {
    return new InvalidMandateException(
        ErrorsResponse.ofSingleError(
            "invalid.mandate.rejoin.not.pending",
            "Only a pending rejoin application can be canceled."));
  }

  public static InvalidMandateException rejoinCancellationDeadlinePassed() {
    return new InvalidMandateException(
        ErrorsResponse.ofSingleError(
            "invalid.mandate.rejoin.cancellation.deadline.passed",
            "The rejoin application can no longer be canceled."));
  }

  public static InvalidMandateException rejoinAlreadyCanceled() {
    return new InvalidMandateException(
        ErrorsResponse.ofSingleError(
            "invalid.mandate.rejoin.already.canceled",
            "The rejoin application has already been canceled."));
  }
}
