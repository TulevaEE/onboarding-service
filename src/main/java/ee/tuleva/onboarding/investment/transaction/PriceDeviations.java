package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PriceDeviations(
    List<PriceDeviation> unconfirmed,
    List<PriceDeviationConfirmation> confirmationsAtAnotherPrice,
    List<PriceDeviation> confirmed,
    Map<String, ManualPriceDeviationCheck> checks) {}
