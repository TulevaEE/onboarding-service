package ee.tuleva.onboarding.comparisons.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="Could not find a pension fund with requested ISIN")
public class IsinNotFoundException extends ComparisonException {

    public IsinNotFoundException(String message) {
        super(message);
    }
}
