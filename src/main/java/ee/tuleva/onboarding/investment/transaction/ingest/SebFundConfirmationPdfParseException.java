package ee.tuleva.onboarding.investment.transaction.ingest;

class SebFundConfirmationPdfParseException extends RuntimeException {

  SebFundConfirmationPdfParseException(String message) {
    super(message);
  }

  SebFundConfirmationPdfParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
