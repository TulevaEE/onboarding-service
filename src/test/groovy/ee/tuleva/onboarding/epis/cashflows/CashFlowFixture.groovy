package ee.tuleva.onboarding.epis.cashflows

import java.time.Instant

class CashFlowFixture {

    static CashFlowStatement cashFlowFixture() {
        def randomTime = Instant.parse("2001-01-01T01:23:45Z")
        return CashFlowStatement.builder()
            .startBalance([
                "1": CashFlow.builder().time(randomTime).amount(1000.0).currency("EUR").isin("1").build(),
                "2": CashFlow.builder().time(randomTime).amount(115.0).currency("EUR").isin("2").build(),
                "3": CashFlow.builder().time(randomTime).amount(225.0).currency("EUR").isin("3").build(),

            ])
            .endBalance([
                "1": CashFlow.builder().time(randomTime).amount(1100.0).currency("EUR").isin("1").build(),
                "2": CashFlow.builder().time(randomTime).amount(125.0).currency("EUR").isin("2").build(),
                "3": CashFlow.builder().time(randomTime).amount(250.0).currency("EUR").isin("3").build(),
            ])
            .transactions([
                CashFlow.builder().time(randomTime).amount(-100.0).currency("EUR").isin("1").build(),
                CashFlow.builder().time(randomTime).amount(-20.0).currency("EUR").isin("2").build(),
                CashFlow.builder().time(randomTime).amount(-25.0).currency("EUR").isin("3").build(),
            ]).build()
    }

}
