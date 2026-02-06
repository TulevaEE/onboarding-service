package ee.tuleva.onboarding.epis.cashflows

import ee.tuleva.onboarding.payment.Payment

import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*

class CashFlowFixture {
  private static final String TULEVA_3RD_PILLAR_FUND_ISIN = "EE3600001707"

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
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-100.0).currency(EUR).isin("1").type(SUBTRACTION).units(10.0).nav(10.0).build(),
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-20.0).currency(EUR).isin("2").type(SUBTRACTION).units(2.0).nav(10.0).build(),
            CashFlow.builder().time(randomTime).priceTime(priceTime).amount(-25.0).currency(EUR).isin("3").type(SUBTRACTION).units(2.5).nav(10.0).build(),
        ]).build()
  }

  static CashFlowStatement cashFlowStatementFor3rdPillarPayment(Payment payment) {
    def time = payment.createdTime.plusSeconds(60)
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
            CashFlow.builder().time(time.plusSeconds(1)).priceTime(time.plusSeconds(1)).amount(amount.negate()).currency(currency).isin(null).type(CASH).build(),
            CashFlow.builder().time(time.plusSeconds(1)).priceTime(time.plusSeconds(1)).amount(10.01).currency(currency).isin(TULEVA_3RD_PILLAR_FUND_ISIN).type(CONTRIBUTION_CASH).units(1.0).nav(10.01).build(),
        ]).build()
  }
}
