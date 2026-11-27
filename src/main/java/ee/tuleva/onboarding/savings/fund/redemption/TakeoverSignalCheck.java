package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal;
import java.util.Optional;

interface TakeoverSignalCheck {

  Optional<TakeoverSignal> raisedBy(RedemptionRequest request);
}
