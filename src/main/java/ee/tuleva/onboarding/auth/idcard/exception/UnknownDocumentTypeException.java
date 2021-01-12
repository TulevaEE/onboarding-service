package ee.tuleva.onboarding.auth.idcard.exception;

public class UnknownDocumentTypeException extends RuntimeException {

    public UnknownDocumentTypeException(String identifier) {
        super("Unknown document type: " + identifier);
    }

    public UnknownDocumentTypeException() {
        super();
    }
}
