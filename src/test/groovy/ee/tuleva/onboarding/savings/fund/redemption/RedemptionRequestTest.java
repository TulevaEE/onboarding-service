package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.MANUAL;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RedemptionRequestTest {

  private static final Instant HELD_AT = Instant.parse("2026-09-28T10:00:00Z");

  @Test
  void neverShowsTheCustomerTheAmlReviewOrTheHold() {
    var request =
        redemptionRequestFixture()
            .status(VERIFIED)
            .holdComment("matches a PEP list entry")
            .holdAt(HELD_AT)
            .heldBy("AML Specialist")
            .holdNotifiedAt(HELD_AT)
            .holdReleasedAt(HELD_AT)
            .requeuedAt(HELD_AT)
            .reviewedBy("AML Specialist")
            .reviewReason("confirmed")
            .reviewedAt(HELD_AT)
            .build();
    request.setHoldReasons(Set.of(PEP));

    var json = JsonMapper.builder().build().writeValueAsString(request);

    assertThat(json)
        .doesNotContain(
            "hold", "Hold", "held", "review", "Review", "requeued", "PEP", "AML Specialist");
  }

  @Test
  void readsHoldReasonsBackInEnumOrderWhateverOrderTheyWereGivenIn() {
    var request = redemptionRequestFixture().build();

    request.setHoldReasons(new LinkedHashSet<>(List.of(MANUAL, HIGH_RISK, PEP)));

    assertThat(request.getHoldReasons()).containsExactly(PEP, HIGH_RISK, MANUAL);
  }
}
