package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface FtCheckOutcome {

  String auditKey();

  Map<String, Object> auditFacts();

  FtConfirmationResult result();

  String describe();
}
