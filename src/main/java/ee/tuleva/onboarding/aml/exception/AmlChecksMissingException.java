package ee.tuleva.onboarding.aml.exception;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class AmlChecksMissingException extends ErrorsResponseException {

    public AmlChecksMissingException(ErrorsResponse errorsResponse) {
        super(errorsResponse);
    }

    public static AmlChecksMissingException newInstance() {
        return new AmlChecksMissingException(
            ErrorsResponse
                .ofSingleError(
                    "invalid.mandate.checks.missing",
                    "Missing some of the required data that is necessary for creating a new mandate.")
        );
    }
}