package ee.tuleva.onboarding.comparisons.overview;

import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
public class AccountOverview {
    private List<Transaction> transactions;
    private BigDecimal beginningBalance;
    private BigDecimal endingBalance;
    private Instant startTime;
    private Instant endTime;
}
