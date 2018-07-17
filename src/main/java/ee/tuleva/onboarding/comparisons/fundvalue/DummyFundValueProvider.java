package ee.tuleva.onboarding.comparisons.fundvalue;

import org.springframework.stereotype.Service;

import java.time.Instant;

// TODO: delete this once we have implemented fund value providers, this is just here so spring boots.
@Service
public class DummyFundValueProvider implements FundValueProvider {
    @Override
    public FundValue getFundValueClosestToTime(Instant time) {
        return null;
    }
}
