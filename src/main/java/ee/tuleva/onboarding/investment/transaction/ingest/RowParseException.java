package ee.tuleva.onboarding.investment.transaction.ingest;

class RowParseException extends RuntimeException {
  RowParseException(String message) {
    super(message);
  }
}
