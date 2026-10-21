package ee.tuleva.onboarding.investment.transaction;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class TradePairing {

  private static final Comparator<OpenTrade> PAIRING_ORDER =
      Comparator.comparing(OpenTrade::reference, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::side)
          .thenComparing(OpenTrade::isin, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::quantity, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::tradeDate, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::settlementDate, nullsFirst(naturalOrder()))
          .thenComparing(OpenTrade::total);

  private final List<OpenTrade> earlier;
  private final List<OpenTrade> later;
  private final Map<Integer, Integer> pairedEarlierByLater = new HashMap<>();

  private TradePairing(List<OpenTrade> earlier, List<OpenTrade> later) {
    this.earlier = earlier.stream().sorted(PAIRING_ORDER).toList();
    this.later = later.stream().sorted(PAIRING_ORDER).toList();
  }

  static OpenTrades.Difference unpaired(List<OpenTrade> earlier, List<OpenTrade> later) {
    var pairing = new TradePairing(earlier, later);
    IntStream.range(0, pairing.earlier.size())
        .forEach(index -> pairing.pairOrRepair(index, new boolean[pairing.later.size()]));
    return pairing.difference();
  }

  private boolean pairOrRepair(int earlierIndex, boolean[] laterTried) {
    for (var laterIndex : candidates(earlier.get(earlierIndex))) {
      if (laterTried[laterIndex]) {
        continue;
      }
      laterTried[laterIndex] = true;
      var pairedEarlier = pairedEarlierByLater.get(laterIndex);
      if (pairedEarlier == null || pairOrRepair(pairedEarlier, laterTried)) {
        pairedEarlierByLater.put(laterIndex, earlierIndex);
        return true;
      }
    }
    return false;
  }

  private List<Integer> candidates(OpenTrade trade) {
    var laterIndexes = IntStream.range(0, later.size()).boxed().toList();
    return Stream.concat(
            laterIndexes.stream().filter(index -> trade.hasSameReferenceAs(later.get(index))),
            laterIndexes.stream()
                .filter(index -> !trade.hasSameReferenceAs(later.get(index)))
                .filter(index -> trade.isSameTradeAs(later.get(index))))
        .toList();
  }

  private OpenTrades.Difference difference() {
    var pairedEarlier = pairedEarlierByLater.values();
    return new OpenTrades.Difference(
        IntStream.range(0, earlier.size())
            .filter(index -> !pairedEarlier.contains(index))
            .mapToObj(earlier::get)
            .toList(),
        IntStream.range(0, later.size())
            .filter(index -> !pairedEarlierByLater.containsKey(index))
            .mapToObj(later::get)
            .toList());
  }
}
