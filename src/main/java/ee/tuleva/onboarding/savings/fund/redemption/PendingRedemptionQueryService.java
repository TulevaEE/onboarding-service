package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.PendingRedemption;
import ee.tuleva.onboarding.savings.RedemptionQueries;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PendingRedemptionQueryService implements RedemptionQueries {

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final SavingFundDeadlinesService deadlinesService;

  @Override
  public List<PendingRedemption> getPendingRedemptions(PartyId partyId) {
    return redemptionRequestRepository
        .findByPartyTypeAndPartyCodeAndStatusIn(
            partyId.type(), partyId.code(), List.of(RESERVED, IN_REVIEW, VERIFIED))
        .stream()
        .map(this::toPendingRedemption)
        .toList();
  }

  private PendingRedemption toPendingRedemption(RedemptionRequest request) {
    return PendingRedemption.builder()
        .id(request.getId())
        .requestedAt(request.getRequestedAt())
        .amount(request.getRequestedAmount())
        .customerIban(request.getCustomerIban())
        .cancellationDeadline(deadlinesService.getCancellationDeadline(request))
        .fulfillmentDeadline(deadlinesService.getFulfillmentDeadline(request))
        .build();
  }
}
