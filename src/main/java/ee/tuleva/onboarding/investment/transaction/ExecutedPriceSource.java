package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Map;

public interface ExecutedPriceSource {

  Map<TulevaFund, Map<String, ExecutedPrice>> executedSellPricesByFund(LocalDate navDate);
}
