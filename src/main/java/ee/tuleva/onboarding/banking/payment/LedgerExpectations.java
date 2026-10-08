package ee.tuleva.onboarding.banking.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LedgerExpectations {
  Optional<BigDecimal> pricedRedemption(UUID redemptionRequestId);

  Optional<BigDecimal> pricedRedemptionBatch(UUID batchId);

  BigDecimal issuedSubscriptions(Instant after, Instant until);

  Optional<BigDecimal> bookedReturn(UUID paymentId);
}
