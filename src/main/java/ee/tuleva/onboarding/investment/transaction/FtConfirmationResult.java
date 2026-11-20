package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PRICE_MISSING;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCONFIRMED;

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

  public boolean isOnlyThePriceMissing() {
    return priceStatus == PRICE_MISSING && !isActionable(quantityStatus);
  }

  private static boolean isActionable(FtVerificationStatus status) {
    return status == ERROR
        || status == AMBIGUOUS
        || status == ORPHAN
        || status == CANCELLED
        || status == UNCHECKED
        || status == UNCONFIRMED
        || status == PRICE_MISSING;
  }
}
