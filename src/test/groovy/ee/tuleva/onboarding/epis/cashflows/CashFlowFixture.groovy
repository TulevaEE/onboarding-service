package ee.tuleva.onboarding.epis.cashflows

import java.time.Instant

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH
import static ee.tuleva.onboarding.mandate.application.PaymentApplicationService.TULEVA_3RD_PILLAR_FUND_ISIN
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aPaymentAmount

class CashFlowFixture {

  static CashFlowStatement cashFlowFixture() {
    def randomTime = Instant.parse("2001-01-02T01:23:45Z")
    def priceTime = Instant.parse("2001-01-01T00:00:00Z")
    return CashFlowStatement.builder()
        .startBalance([
            "1": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(1000.0).currency("EUR").isin("1").build(),
            "2": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(115.0).currency("EUR").isin("2").build(),
            "3": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(225.0).currency("EUR").isin("3").build(),

        ])
        .endBalance([
            "1": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(1100.0).currency("EUR").isin("1").build(),
            "2": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(125.0).currency("EUR").isin("2").build(),
            "3": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(250.0).currency("EUR").isin("3").build(),
        ])
        .transactions([
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-100.0).currency("EUR").isin("1").build(),
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-20.0).currency("EUR").isin("2").build(),
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-25.0).currency("EUR").isin("3").build(),
        ]).build()
  }

  static CashFlowStatement cashFlowFixtureThatMatchesPayment() {
    def time = Instant.now().plus(60)
    return CashFlowStatement.builder()
        .startBalance([
            "1": CashFlow.builder().time(time).priceTime(time).amount(1000.0).currency("EUR").isin("1").build(),

        ])
        .endBalance([
            "1": CashFlow.builder().time(time).priceTime(time).amount(1010.0).currency("EUR").isin("1").build(),
        ])
        .transactions([
            CashFlow.builder().time(time).priceTime(time).amount(aPaymentAmount).currency("EUR").isin(null).type(CASH).build(),
            CashFlow.builder().time(time.plus(1)).priceTime(time.plus(1)).amount(aPaymentAmount.negate()).currency("EUR").isin(null).type(CASH).build(),
            CashFlow.builder().time(time.plus(1)).priceTime(time.plus(1)).amount(10.01).currency("EUR").isin(TULEVA_3RD_PILLAR_FUND_ISIN).type(CONTRIBUTION_CASH).build(),
        ]).build()
  }
}
