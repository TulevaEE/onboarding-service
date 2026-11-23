package ee.tuleva.onboarding.investment.transaction.ingest;

import java.util.List;

class SebFundConfirmationBackfillIncompleteException extends IllegalStateException {

  private final transient LeftOut leftOut;

  SebFundConfirmationBackfillIncompleteException(
      List<String> unreadableKeys, List<String> failedKeys) {
    super(
        "SEB fund confirmation backfill left PDFs out: unreadableKeys="
            + unreadableKeys
            + ", failedKeys="
            + failedKeys);
    this.leftOut = new LeftOut(List.copyOf(unreadableKeys), List.copyOf(failedKeys));
  }

  LeftOut leftOut() {
    return leftOut;
  }

  record LeftOut(List<String> unreadableKeys, List<String> failedKeys) {}
}
