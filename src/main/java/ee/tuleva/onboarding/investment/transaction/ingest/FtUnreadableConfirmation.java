package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;

import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtUnreadableConfirmation(String key, String reason) implements FtCheckOutcome {

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
    return "FT confirmation PDF could not be read: key=%s, error=%s — check the trade in this PDF against its order by hand"
        .formatted(key, reason);
  }
}
