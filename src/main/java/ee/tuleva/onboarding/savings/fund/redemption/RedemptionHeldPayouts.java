package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PAYOUT_HELD;

import ee.tuleva.onboarding.banking.payment.HeldPayouts;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedemptionHeldPayouts implements HeldPayouts {

  private final RedemptionRequestRepository redemptionRequestRepository;

  @Override
  public BigDecimal heldIn(UUID batchId) {
    return redemptionRequestRepository.sumCashAmount(batchId, PAYOUT_HELD);
  }
}
