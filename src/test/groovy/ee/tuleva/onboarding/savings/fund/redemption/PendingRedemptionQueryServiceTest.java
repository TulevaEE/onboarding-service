package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.PendingRedemption;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingRedemptionQueryServiceTest {

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private SavingFundDeadlinesService deadlinesService;
  @InjectMocks private PendingRedemptionQueryService queryService;

  @Test
  void mapsPendingRedemptionsWithDeadlines() {
    var partyId = new PartyId(PartyId.Type.PERSON, "38888888888");
    var id = UUID.randomUUID();
    var requestedAt = Instant.parse("2021-03-30T10:00:00Z");
    var cancellationDeadline = Instant.parse("2021-03-31T21:00:00Z");
    var fulfillmentDeadline = Instant.parse("2021-04-20T10:00:00Z");
    var request =
        RedemptionRequest.builder()
            .id(id)
            .userId(1L)
            .partyId(partyId)
            .requestedAmount(new BigDecimal("150.00"))
            .customerIban("EE123456789012345678")
            .status(RESERVED)
            .requestedAt(requestedAt)
            .build();
    given(
            redemptionRequestRepository.findByPartyTypeAndPartyCodeAndStatusIn(
                partyId.type(), partyId.code(), List.of(RESERVED, IN_REVIEW, VERIFIED)))
        .willReturn(List.of(request));
    given(deadlinesService.getCancellationDeadline(request)).willReturn(cancellationDeadline);
    given(deadlinesService.getFulfillmentDeadline(request)).willReturn(fulfillmentDeadline);

    var pendingRedemptions = queryService.getPendingRedemptions(partyId);

    assertThat(pendingRedemptions)
        .containsExactly(
            PendingRedemption.builder()
                .id(id)
                .requestedAt(requestedAt)
                .amount(new BigDecimal("150.00"))
                .customerIban("EE123456789012345678")
                .cancellationDeadline(cancellationDeadline)
                .fulfillmentDeadline(fulfillmentDeadline)
                .build());
  }
}
