package ee.tuleva.onboarding.epis.cashflows

import ee.tuleva.onboarding.payment.Payment

import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*
import static ee.tuleva.onboarding.mandate.application.PaymentLinkingService.TULEVA_3RD_PILLAR_FUND_ISIN

class CashFlowFixture {

  static CashFlowStatement cashFlowFixture() {
    def randomTime = Instant.parse("2001-01-02T01:23:45Z")
    def priceTime = Instant.parse("2001-01-01T00:00:00Z")
    return CashFlowStatement.builder()
        .startBalance([
            "1": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(1000.0).currency(EUR).isin("1").build(),
            "2": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(115.0).currency(EUR).isin("2").build(),
            "3": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(225.0).currency(EUR).isin("3").build(),
        ])
        .endBalance([
            "1": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(1100.0).currency(EUR).isin("1").build(),
            "2": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(125.0).currency(EUR).isin("2").build(),
            "3": CashFlow.builder().time(randomTime).priceTime(priceTime).amount(250.0).currency(EUR).isin("3").build(),
        ])
        .transactions([
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-100.0).currency(EUR).isin("1").type(SUBTRACTION).comment("sub1").build(),
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-20.0).currency(EUR).isin("2").type(SUBTRACTION).comment("sub2").build(),
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-25.0).currency(EUR).isin("3").type(SUBTRACTION).comment("sub3").build(),
        ]).build()
  }

  static CashFlowStatement cashFlowStatementFor3rdPillarPayment(Payment payment) {
    def time = payment.createdTime.plus(60)
    def amount = payment.amount
    def currency = payment.currency
    return CashFlowStatement.builder()
        .startBalance([
            "1": CashFlow.builder().time(time).priceTime(time).amount(0.0).currency(currency).isin(null).build()
        ])
        .endBalance([
            "1": CashFlow.builder().time(time).priceTime(time).amount(0.0).currency(currency).isin(null).build()
        ])
        .transactions([
            CashFlow.builder().time(time).priceTime(time).amount(amount).currency(currency).isin(null).type(CASH).build(),
            CashFlow.builder().time(time.plus(1)).priceTime(time.plus(1)).amount(amount.negate()).currency(currency).isin(null).type(CASH).build(),
            CashFlow.builder().time(time.plus(1)).priceTime(time.plus(1)).amount(10.01).currency(currency).isin(TULEVA_3RD_PILLAR_FUND_ISIN).type(CONTRIBUTION_CASH).build(),
        ]).build()
  }
}
