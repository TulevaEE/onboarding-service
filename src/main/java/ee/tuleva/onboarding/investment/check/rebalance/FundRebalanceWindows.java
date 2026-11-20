package ee.tuleva.onboarding.investment.check.rebalance;

import java.util.List;
import java.util.Optional;

record FundRebalanceWindows(Optional<RebalanceWindow> current, List<SupersededWindow> superseded) {

  static final FundRebalanceWindows NONE = new FundRebalanceWindows(Optional.empty(), List.of());
}
