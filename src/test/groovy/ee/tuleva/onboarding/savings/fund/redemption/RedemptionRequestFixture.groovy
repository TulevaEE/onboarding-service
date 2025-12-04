package ee.tuleva.onboarding.savings.fund.redemption


import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.RedemptionRequestBuilder
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED

class RedemptionRequestFixture {

  static RedemptionRequestBuilder redemptionRequestFixture() {
    return RedemptionRequest.builder()
        .userId(1L)
        .fundUnits(new BigDecimal("10.00000"))
        .requestedAmount(new BigDecimal("10.00"))
        .customerIban("EE123456789012345678")
        .status(RESERVED)
  }

  static aRedemptionRequest = redemptionRequestFixture().build()

}
