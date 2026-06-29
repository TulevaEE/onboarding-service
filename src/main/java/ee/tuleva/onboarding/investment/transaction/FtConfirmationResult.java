package ee.tuleva.onboarding.investment.transaction;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record FtConfirmationResult(
    FtVerificationStatus quantityStatus,
    FtVerificationStatus priceStatus,
    Map<String, String> details) {}
