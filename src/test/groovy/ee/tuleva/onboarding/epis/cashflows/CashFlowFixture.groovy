package ee.tuleva.onboarding.epis.cashflows

import java.time.Instant

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

}
