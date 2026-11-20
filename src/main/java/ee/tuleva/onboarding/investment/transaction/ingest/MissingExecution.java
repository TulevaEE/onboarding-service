package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.AWAITING_SEB_REPORT;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record MissingExecution(Stage stage, Map<String, String> details) {

  enum Stage {
    AWAITING_SEB_REPORT,
    MISSING_AFTER_SEB_REPORT,
    SEB_REPORT_NOT_RECONCILED
  }

  MissingExecution {
    details = Map.copyOf(details);
  }

  boolean awaitsSebReport() {
    return stage == AWAITING_SEB_REPORT;
  }
}
