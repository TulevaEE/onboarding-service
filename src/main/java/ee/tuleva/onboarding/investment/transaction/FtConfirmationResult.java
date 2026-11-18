package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record FtConfirmationResult(
    FtVerificationStatus quantityStatus,
    FtVerificationStatus priceStatus,
    Map<String, String> details) {

  public boolean isActionable() {
    return isActionable(quantityStatus) || isActionable(priceStatus);
  }

  private static boolean isActionable(FtVerificationStatus status) {
    return status == ERROR
        || status == AMBIGUOUS
        || status == ORPHAN
        || status == CANCELLED
        || status == UNCHECKED;
  }
}
