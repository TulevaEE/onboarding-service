package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record CalculatedOrders(
    List<TransactionOrder> orders, Map<String, @Nullable ResolvedPrice> priceResolutions) {}
