package ee.tuleva.onboarding.investment.transaction.ingest;

import org.jspecify.annotations.NullMarked;

@NullMarked
class FtConfirmationPdfParseException extends RuntimeException {

  FtConfirmationPdfParseException(String message) {
    super(message);
  }

  FtConfirmationPdfParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
