package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.ordersWithTheirOwnConfirmation;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtCorrections(List<FtConfirmation> confirmations) {

  static FtCorrections among(List<FtConfirmation> confirmations) {
    return new FtCorrections(
        confirmations.stream()
            .filter(Objects::nonNull)
            .filter(confirmation -> !confirmation.suppressed())
            .toList());
  }

  boolean isVoided(FtConfirmation confirmation) {
    return confirmations.stream().anyMatch(confirmation::isCancelledBy);
  }

  boolean newConfirmationsCoverEveryOrder(
      FtConfirmation cancellation, List<TransactionOrder> liveOrders) {
    return !liveOrders.isEmpty()
        && ordersWithTheirOwnConfirmation(liveOrders, newConfirmationsAfter(cancellation)).size()
            == liveOrders.size();
  }

  List<FtConfirmation> standingConfirmations(Predicate<FtConfirmation> awaitingReplacement) {
    return distinctTrades(
        confirmations.stream()
            .filter(
                confirmation ->
                    confirmation.isCancellation()
                        ? confirmations.stream()
                                .noneMatch(other -> other.isCancelledBy(confirmation))
                            && awaitingReplacement.test(confirmation)
                        : confirmations.stream()
                            .filter(confirmation::isCancelledBy)
                            .allMatch(awaitingReplacement)));
  }

  private List<FtConfirmation> newConfirmationsAfter(FtConfirmation cancellation) {
    return distinctTrades(
        confirmations.stream()
            .filter(confirmation -> confirmation.isNewConfirmationAfter(cancellation))
            .filter(confirmation -> !isVoided(confirmation)));
  }

  private static List<FtConfirmation> distinctTrades(Stream<FtConfirmation> confirmations) {
    return confirmations
        .collect(
            toMap(
                FtConfirmationAuditRecorder::dedupKey,
                confirmation -> confirmation,
                (first, sameTrade) -> first,
                LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }
}
