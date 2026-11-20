package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;

import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtUnreadableConfirmation(String key, String reason) implements FtCheckOutcome {

  private static final String CHECK_THE_TRADE_IN_THE_PDF_BY_HAND =
      "check the trade in the unreadable PDF against its order by hand";

  @Override
  public String auditKey() {
    return "FT_PDF|" + key;
  }

  @Override
  public Map<String, Object> auditFacts() {
    return Map.of("key", key);
  }

  @Override
  public FtConfirmationResult result() {
    return new FtConfirmationResult(UNCHECKED, UNCHECKED, Map.of("verificationError", reason));
  }

  @Override
  public String describe() {
    return "FT confirmation PDF could not be read: key=%s, error=%s".formatted(key, reason);
  }

  @Override
  public List<String> nextSteps() {
    return List.of(CHECK_THE_TRADE_IN_THE_PDF_BY_HAND);
  }
}
