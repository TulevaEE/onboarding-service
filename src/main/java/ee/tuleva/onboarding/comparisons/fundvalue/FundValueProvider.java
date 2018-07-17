package ee.tuleva.onboarding.comparisons.fundvalue;

import java.time.Instant;

public interface FundValueProvider {
    FundValue getFundValueClosestToTime(Instant time);
}
