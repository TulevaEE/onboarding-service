package ee.tuleva.onboarding.auth.smartid;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

import java.util.List;

public class SmartIdException extends ErrorsResponseException {

    public SmartIdException(ErrorsResponse errorsResponse) {
        super(errorsResponse);
    }

    public static SmartIdException ofErrors(List<String> errors) {
        ErrorsResponse errorsResponse = new ErrorsResponse();
        for (int i = 0; i < errors.size(); i++) {
            errorsResponse.add(ErrorResponse.builder().code("smart.id.error." + i).message(errors.get(i)).build());
        }
        return new SmartIdException(errorsResponse);
    }

}