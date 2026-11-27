package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record CalculatedOrders(
    List<TransactionOrder> orders,
    Map<String, @Nullable TradePrepPrice> priceResolutions,
    List<SizedSale> sizedSales) {

  List<String> droppedSales() {
    return sizedSales.stream().filter(SizedSale::dropped).map(SizedSale::isin).distinct().toList();
  }
}
