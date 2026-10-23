package ee.tuleva.onboarding.investment.transaction;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

final class TradePairing {

  private static final Comparator<OpenTrade> PAIRING_ORDER =
      Comparator.comparing(OpenTrade::reference, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::side)
          .thenComparing(OpenTrade::isin, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::quantity, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::tradeDate, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::settlementDate, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::total);
  private static final long SAME_REFERENCE_BONUS = 1;

  private TradePairing() {}

  static OpenTrades.Difference unpaired(List<OpenTrade> earlier, List<OpenTrade> later) {
    var sortedEarlier = earlier.stream().sorted(PAIRING_ORDER).toList();
    var sortedLater = later.stream().sorted(PAIRING_ORDER).toList();
    var pairWeight =
        weightOutweighingEverySameReferenceBonus(
            Math.max(sortedEarlier.size(), sortedLater.size()));
    var weights =
        sortedEarlier.stream()
            .map(
                trade ->
                    sortedLater.stream()
                        .mapToLong(other -> weight(trade, other, pairWeight))
                        .toArray())
            .toArray(long[][]::new);
    var laterOfEarlier = MaximumWeightAssignment.columnsOfRows(weights, sortedLater.size());
    var pairedLater = Set.copyOf(laterOfEarlier.values());
    return new OpenTrades.Difference(
        IntStream.range(0, sortedEarlier.size())
            .filter(index -> !laterOfEarlier.containsKey(index))
            .mapToObj(sortedEarlier::get)
            .toList(),
        IntStream.range(0, sortedLater.size())
            .filter(index -> !pairedLater.contains(index))
            .mapToObj(sortedLater::get)
            .toList());
  }

  private static long weightOutweighingEverySameReferenceBonus(int mostPairs) {
    return (mostPairs + 1) * SAME_REFERENCE_BONUS;
  }

  private static long weight(OpenTrade earlier, OpenTrade later, long pairWeight) {
    if (!earlier.isSameTradeAs(later)) {
      return 0;
    }
    return earlier.hasSameReferenceAs(later) ? pairWeight + SAME_REFERENCE_BONUS : pairWeight;
  }
}
