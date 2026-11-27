package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.LARGE_PAYOUT;

import ee.tuleva.onboarding.ledger.SavingsFundUnits;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LargePayoutCheck implements TakeoverSignalCheck {

  private static final BigDecimal LARGE_PAYOUT_ABOVE_EUR = new BigDecimal("10000");
  private static final BigDecimal LARGE_SHARE_OF_UNITS_HELD_ABOVE = new BigDecimal("0.8");

  private final SavingsFundUnits savingsFundUnits;

  @Override
  public Optional<TakeoverSignal> raisedBy(RedemptionRequest request) {
    return isAboveTheAmount(request) || isAboveTheShareOfUnitsHeld(request)
        ? Optional.of(LARGE_PAYOUT)
        : Optional.empty();
  }

  private static boolean isAboveTheAmount(RedemptionRequest request) {
    return request.getRequestedAmount().compareTo(LARGE_PAYOUT_ABOVE_EUR) > 0;
  }

  private boolean isAboveTheShareOfUnitsHeld(RedemptionRequest request) {
    return request
            .getFundUnits()
            .compareTo(LARGE_SHARE_OF_UNITS_HELD_ABOVE.multiply(unitsHeldJustBefore(request)))
        > 0;
  }

  private BigDecimal unitsHeldJustBefore(RedemptionRequest request) {
    return savingsFundUnits
        .unitsHeldAt(LedgerRefs.from(request.getPartyId()), request.getRequestedAt().minusNanos(1))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Redemption party has no unit accounts: id="
                        + request.getId()
                        + ", party="
                        + request.getPartyId()));
  }
}
