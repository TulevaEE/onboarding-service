package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PriceDeviations(
    List<PriceDeviation> unconfirmed,
    List<PriceDeviationConfirmation> confirmationsAtAnotherPrice,
    List<PriceDeviation> confirmed,
    List<String> withoutIndexPriceHistory) {}
