package ee.tuleva.onboarding.capital

import static ee.tuleva.onboarding.capital.CapitalStatement.*

class CapitalStatementFixture {
    static CapitalStatementBuilder fixture() {
        builder()
        .capitalPayment(new BigDecimal((new Random()).nextDouble() * 1000))
        .membershipBonus(new BigDecimal((new Random()).nextDouble() * 1000))
        .unvestedWorkCompensation(new BigDecimal((new Random()).nextDouble() * 1000))
        .workCompensation(new BigDecimal((new Random()).nextDouble() * 1000))
        .profit(new BigDecimal((new Random()).nextDouble() * 1000))
    }
}
