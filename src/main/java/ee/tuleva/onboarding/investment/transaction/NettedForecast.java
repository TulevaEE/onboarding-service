package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record NettedForecast(String name, BigDecimal before, BigDecimal after, Treatment treatment) {

  enum Treatment {
    NETTED_AGAINST_THE_BALANCE,
    NOT_NETTED
  }

  static List<NettedForecast> onTheGatewayBalance(
      Netted<UnsettledTrades> trades,
      PendingOrderImpact pendingOrders,
      Netted<BigDecimal> pevaRava,
      Netted<BigDecimal> r45,
      Netted<BigDecimal> unitReceivable,
      BigDecimal incomingPaymentsClearing) {
    return List.of(
        netted(
            "unsettledTradePayables",
            new Netted<>(trades.before().payables(), trades.after().payables())),
        netted(
            "unsettledTradeReceivables",
            new Netted<>(trades.before().receivables(), trades.after().receivables())),
        netted(
            "pendingBuys",
            new Netted<>(
                pendingOrders.pendingBuys().add(pendingOrders.buysInTheBalance()),
                pendingOrders.pendingBuys())),
        netted(
            "pendingSells",
            new Netted<>(
                pendingOrders.pendingSells().add(pendingOrders.sellsInTheBalance()),
                pendingOrders.pendingSells())),
        netted("pevaRava", pevaRava),
        netted("r45", r45),
        netted("unitReceivable", unitReceivable),
        notNetted("incomingPaymentsClearing", incomingPaymentsClearing));
  }

  static NettedForecast netted(String name, Netted<BigDecimal> netted) {
    return new NettedForecast(
        name, netted.before(), netted.after(), Treatment.NETTED_AGAINST_THE_BALANCE);
  }

  static NettedForecast notNetted(String name, BigDecimal amount) {
    return new NettedForecast(name, amount, amount, Treatment.NOT_NETTED);
  }

  boolean isInPlay() {
    return before.signum() != 0 || after.signum() != 0;
  }

  Map<String, Object> payload() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", name);
    map.put("before", before.toPlainString());
    map.put("after", after.toPlainString());
    map.put("treatment", treatment.name());
    return map;
  }
}
