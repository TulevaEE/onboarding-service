package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface FtCheckOutcome {

  String CHECK_THE_PRICE_AGAINST_THE_OFFICIAL_CLOSE_BY_HAND =
      "check the price against the official close by hand";

  String auditKey();

  Map<String, Object> auditFacts();

  FtConfirmationResult result();

  String describe();

  List<String> nextSteps();
}
