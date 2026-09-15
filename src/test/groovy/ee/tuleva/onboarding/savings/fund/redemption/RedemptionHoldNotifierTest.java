package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FROZEN;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PAYOUT_HELD;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionHoldNotifierTest {

  private static final UUID REQUEST_ID = UUID.fromString("2db696b5-00ee-4937-87b4-8192c675e4b5");

  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private RedemptionHoldNotifier notifier;

  @Test
  void notifyHold_describesAFrozenOrderAsASanctionsHit() {
    var request =
        redemptionRequestFixture().id(REQUEST_ID).status(FROZEN).holdReason("SANCTION").build();

    assertThat(notifier.notifyHold(request)).isTrue();

    verify(notificationService)
        .sendMessage(
            "AML: SANCTIONS HIT, redemption frozen: id=2db696b5-00ee-4937-87b4-8192c675e4b5, "
                + "amount=10.00 EUR. The order is not executed until released: "
                + "POST /admin/redemptions/2db696b5-00ee-4937-87b4-8192c675e4b5/release",
            AML);
  }

  @Test
  void notifyHold_describesAFlaggedOrderAsAHeldPayout() {
    var request =
        redemptionRequestFixture()
            .id(REQUEST_ID)
            .status(VERIFIED)
            .holdReason("PEP,HIGH_RISK")
            .build();

    assertThat(notifier.notifyHold(request)).isTrue();

    verify(notificationService)
        .sendMessage(
            "AML: redemption will be executed but the payout is held for review: "
                + "id=2db696b5-00ee-4937-87b4-8192c675e4b5, amount=10.00 EUR, reason=PEP,HIGH_RISK. "
                + "Release: POST /admin/redemptions/2db696b5-00ee-4937-87b4-8192c675e4b5/release",
            AML);
  }

  @Test
  void notifyPayoutHeldAtPricing_reportsThePricedAmount() {
    var request =
        redemptionRequestFixture()
            .id(REQUEST_ID)
            .status(PAYOUT_HELD)
            .holdReason("PEP")
            .cashAmount(new BigDecimal("25.00"))
            .navPerUnit(new BigDecimal("2.50000"))
            .build();

    assertThat(notifier.notifyPayoutHeldAtPricing(request)).isTrue();

    verify(notificationService)
        .sendMessage(
            "AML: redemption priced, payout held for review: "
                + "id=2db696b5-00ee-4937-87b4-8192c675e4b5, cashAmount=25.00 EUR, NAV=2.50000",
            AML);
  }

  @Test
  void notifyReleased_carriesOnlyTheRequestId() {
    assertThat(notifier.notifyReleased(REQUEST_ID)).isTrue();

    verify(notificationService)
        .sendMessage("AML: redemption released: id=2db696b5-00ee-4937-87b4-8192c675e4b5", AML);
  }

  @Test
  void notifyUnscreened_countsTheWaitingRequests() {
    assertThat(notifier.notifyUnscreened(3)).isTrue();

    verify(notificationService)
        .sendMessage(
            "AML: 3 redemption request(s) unscreened for over an hour, is the screening service down?",
            AML);
  }

  @Test
  void notifyHold_reportsADeliveryFailureInsteadOfThrowing() {
    var request =
        redemptionRequestFixture().id(REQUEST_ID).status(FROZEN).holdReason("SANCTION").build();
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(anyString(), any());

    assertThat(notifier.notifyHold(request)).isFalse();
  }
}
