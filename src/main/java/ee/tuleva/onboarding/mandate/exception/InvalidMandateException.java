package ee.tuleva.onboarding.mandate.exception;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class InvalidMandateException extends ErrorsResponseException {

    public InvalidMandateException(ErrorsResponse errorsResponse) {
        super(errorsResponse);
    }

    public static InvalidMandateException amlChecksMissing() {
        return new InvalidMandateException(
            ErrorsResponse
                .ofSingleError(
                    "invalid.mandate.checks.missing",
                    "Missing some of the required data that is necessary for creating a new mandate.")
        );
    }

    public static InvalidMandateException sourceAmountExceeded() {
        return new InvalidMandateException(
            ErrorsResponse
                .ofSingleError(
                    "invalid.mandate.source.amount.exceeded",
                    "Source amount exceeded.")
        );
    }

    public static InvalidMandateException sameSourceAndTargetTransferPresent() {
        return new InvalidMandateException(
            ErrorsResponse
                .ofSingleError(
                    "invalid.mandate.same.source.and.target.transfer.present",
                    "Same source and target transfer present.")
        );
    }

}
