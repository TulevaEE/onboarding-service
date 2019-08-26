package ee.tuleva.onboarding.epis.cashflows


import java.time.LocalDate

class CashFlowFixture {

    public static CashFlowStatement cashFlowFixture() {
        def randomDate = LocalDate.parse("2001-01-01")
        return CashFlowStatement.builder()
            .startBalance([
                "1": CashFlow.builder().date(randomDate).amount(1000.0).currency("EEK").isin("1").build(),
                "2": CashFlow.builder().date(randomDate).amount(115.0).currency("EUR").isin("2").build(),
                "3": CashFlow.builder().date(randomDate).amount(225.0).currency("EUR").isin("3").build(),

            ])
            .endBalance([
                "1": CashFlow.builder().date(randomDate).amount(1100.0).currency("EEK").isin("1").build(),
                "2": CashFlow.builder().date(randomDate).amount(125.0).currency("EUR").isin("2").build(),
                "3": CashFlow.builder().date(randomDate).amount(250.0).currency("EUR").isin("3").build(),
            ])
            .transactions([
                CashFlow.builder().date(randomDate).amount(-100.0).currency("EEK").isin("1").build(),
                CashFlow.builder().date(randomDate).amount(-20.0).currency("EUR").isin("2").build(),
                CashFlow.builder().date(randomDate).amount(-25.0).currency("EUR").isin("3").build(),
            ]).build()
    }

}
