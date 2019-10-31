package ee.tuleva.onboarding.account

import spock.lang.Specification
import spock.lang.Unroll

class FundBalanceSpec extends Specification {

    @Unroll
    def "calculates profit #value + #unavailableValue - #contributionSum = #expectedProfit"() {
        given:
        def fundBalance = FundBalance.builder()
            .contributionSum(contributionSum)
            .value(value)
            .unavailableValue(unavailableValue)
            .build()

        when:
        def profit = fundBalance.getProfit()

        then:
        profit == expectedProfit

        where:
        contributionSum | value | unavailableValue || expectedProfit
        null            | null  | null             || null
        null            | null  | 1.0              || null
        null            | 110.0 | null             || null
        null            | 110.0 | 1.0              || null
        100.0           | null  | null             || null
        100.0           | null  | 1.0              || null
        100.0           | 110.0 | null             || 10.0
        100.0           | 110.0 | 1.0              || 11.0
    }

    @Unroll
    def "calculates total value #value + #unavailableValue = #expectedTotal"() {
        given:
        def fundBalance = FundBalance.builder()
            .value(value)
            .unavailableValue(unavailableValue)
            .build()

        when:
        def totalValue = fundBalance.getTotalValue()

        then:
        totalValue == expectedTotal

        where:
        value | unavailableValue || expectedTotal
        null  | null             || 0.0
        null  | 1.0              || 1.0
        1.0   | null             || 1.0
        1.0   | 1.0              || 2.0
    }
}
