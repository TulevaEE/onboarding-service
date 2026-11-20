package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.ManualPriceDeviates;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PriceCheck(
    List<InputGateFailure> failures,
    TradePrepPricing pricing,
    List<UsedPriceOverride> priceOverrides,
    List<PriceDeviation> confirmedDeviations,
    List<String> withoutIndexPriceHistory) {

  PriceCheck withDeviations(PriceDeviations deviations) {
    Stream<InputGateFailure> unconfirmed =
        deviations.unconfirmed().isEmpty()
            ? Stream.empty()
            : Stream.of(
                new ManualPriceDeviates(
                    deviations.unconfirmed(), deviations.confirmationsAtAnotherPrice()));
    return new PriceCheck(
        Stream.concat(failures.stream(), unconfirmed).toList(),
        pricing,
        priceOverrides,
        deviations.confirmed(),
        deviations.withoutIndexPriceHistory());
  }
}
