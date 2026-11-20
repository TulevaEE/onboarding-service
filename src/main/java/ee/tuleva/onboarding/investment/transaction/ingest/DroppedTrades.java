package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import java.util.Set;

record DroppedTrades(List<DroppedTrade> trades, Set<TulevaFund> fundsNotChecked) {}
