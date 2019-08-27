package ee.tuleva.onboarding.account

import spock.lang.Specification
import spock.lang.Unroll

class FundBalanceSpec extends Specification {

    @Unroll
    def "calculates profit #value - #contributionSum = #expectedProfit"(BigDecimal contributionSum, BigDecimal value, BigDecimal expectedProfit) {
        given:
        def fundBalance = FundBalance.builder().contributionSum(contributionSum).value(value).build()

        when:
        def profit = fundBalance.getProfit()

        then:
        profit == expectedProfit

        where:
        contributionSum | value | expectedProfit
        100.0           | 110.0 | 10.0
        null            | 110.0 | null
        100.0           | null  | null
    }
}
