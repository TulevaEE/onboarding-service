package ee.tuleva.onboarding.mandate.cancellation;

public class InvalidApplicationTypeException extends RuntimeException {

    public InvalidApplicationTypeException(String message) {
        super(message);
    }
}
