package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import ee.tuleva.onboarding.comparisons.overview.AccountOverview
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider
import ee.tuleva.onboarding.comparisons.overview.Transaction
import ee.tuleva.onboarding.comparisons.returns.RateOfReturnCalculator
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class FundComparisonCalculatorServiceSpec extends Specification {

    AccountOverviewProvider accountOverviewProvider
    FundValueProvider fundValueProvider
    RateOfReturnCalculator rateOfReturnCalculator

    FundComparisonCalculatorService fundComparisonCalculatorService

    void setup() {
        accountOverviewProvider = Mock(AccountOverviewProvider)
        fundValueProvider = Mock(FundValueProvider)
        rateOfReturnCalculator = new RateOfReturnCalculator(fundValueProvider)

        fundComparisonCalculatorService = new FundComparisonCalculatorService(
            accountOverviewProvider,
            rateOfReturnCalculator
        )
    }

    def "it uses an account overview for the given person and start time"() {
        given:
        Person person = Mock(Person)
        Instant startTime = parseInstant("2018-06-17")
        Instant endTime = parseInstant("2018-06-18")
        fakeNoReturnFundValues()
        when:
        fundComparisonCalculatorService.calculateComparison(person, startTime, 2)
        then:
        1 * accountOverviewProvider.getAccountOverview(_ as Person, _ as Instant, _ as Integer) >> new AccountOverview([
            new Transaction(100.0, startTime),
            new Transaction(100.0, startTime),
        ], 0.0, 200.0, startTime, endTime, 2)
    }

    def "it successfully calculates a return of 0%"() {
        given:
        Instant startTime = parseInstant("2018-06-17")
        Instant endTime = parseInstant("2018-06-18")
        fakeNoReturnFundValues()
        accountOverviewProvider.getAccountOverview(_ as Person, _ as Instant, _ as Integer) >> new AccountOverview([
            new Transaction(100.0, startTime),
            new Transaction(100.0, startTime),
        ], 0.0, 200.0, startTime, endTime, 2)
        when:
        FundComparison comparison = fundComparisonCalculatorService.calculateComparison(_ as Person, startTime, 2)
        then:
        comparison.actualReturnPercentage == 0
        comparison.estonianAverageReturnPercentage == 0
        comparison.marketAverageReturnPercentage == 0
    }

    @Unroll
    def "it successfully calculates a return for 0-valued transactions"() {
        given:
        Instant startTime = parseInstant("2018-06-17")
        Instant endTime = parseInstant("2018-06-18")
        fakeNoReturnFundValues()
        accountOverviewProvider.getAccountOverview(_ as Person, _ as Instant, _ as Integer) >> new AccountOverview([
            new Transaction(firstTransaction, startTime),
            new Transaction(secondTransaction, startTime),
        ], beginningBalance, endingBalance, startTime, endTime, 2)
        when:
        FundComparison comparison = fundComparisonCalculatorService.calculateComparison(_ as Person, startTime, 2)
        then:
        comparison.actualReturnPercentage == xirr.doubleValue()
        comparison.estonianAverageReturnPercentage == 0
        comparison.marketAverageReturnPercentage == 0
        where:
        firstTransaction | secondTransaction | beginningBalance | endingBalance || xirr
        0.0              | 0.0               | 0.0              | 0.0           || 0.0
        0.0              | 1.0               | 0.0              | 1.0           || 0.0
        0.0              | -1.0              | 1.0              | 0.0           || 0.0
    }

    def "it correctly calculates actual return taking into account the beginning balance"() {
        given:
        fakeNoReturnFundValues()

        Instant startTime = parseInstant("2010-01-01")
        Instant endTime = parseInstant("2018-07-18")
        accountOverviewProvider.getAccountOverview(_, _, _) >> new AccountOverview([
            new Transaction(30.0, parseInstant("2010-07-01")),
            new Transaction(30.0, parseInstant("2011-01-01")),
            new Transaction(30.0, parseInstant("2011-07-01")),
            new Transaction(30.0, parseInstant("2012-01-01")),
            new Transaction(30.0, parseInstant("2012-07-01")),
            new Transaction(30.0, parseInstant("2013-01-01")),
            new Transaction(30.0, parseInstant("2013-07-01")),
            new Transaction(30.0, parseInstant("2014-01-01")),
            new Transaction(30.0, parseInstant("2014-07-01")),
            new Transaction(30.0, parseInstant("2015-01-01")),
            new Transaction(30.0, parseInstant("2015-07-01")),
            new Transaction(30.0, parseInstant("2016-01-01")),
            new Transaction(30.0, parseInstant("2016-07-01")),
            new Transaction(30.0, parseInstant("2017-01-01")),
            new Transaction(30.0, parseInstant("2017-07-01")),
            new Transaction(30.0, parseInstant("2018-01-01")),
        ], 30.0, 620.0, startTime, endTime, 2)
        when:
        FundComparison comparison = fundComparisonCalculatorService.calculateComparison(null, startTime, 2)
        then:
        comparison.actualReturnPercentage == 0.0427.doubleValue()
        comparison.estonianAverageReturnPercentage == 0
        comparison.marketAverageReturnPercentage == 0
    }

    def "it correctly calculates simulated return using a different fund taking into account the beginning balance"() {
        given:
        Instant startTime = parseInstant("2010-01-01")
        Instant endTime = parseInstant("2018-07-16")
        mockFundValues(EPIFundValueRetriever.KEY, getEpiFundValuesMap())
        fundValueProvider.getLatestValue(WorldIndexValueRetriever.KEY, _) >> {
            String givenFund, LocalDate date -> Optional.of(new FundValue(null, 123.0, WorldIndexValueRetriever.KEY))
        }
        accountOverviewProvider.getAccountOverview(_, _, _) >> new AccountOverview([
            new Transaction(30.0, parseInstant("2010-07-01")),
            new Transaction(30.0, parseInstant("2011-01-01")),
            new Transaction(30.0, parseInstant("2011-07-01")),
            new Transaction(30.0, parseInstant("2012-01-01")),
            new Transaction(30.0, parseInstant("2012-07-01")),
            new Transaction(30.0, parseInstant("2013-01-01")),
            new Transaction(30.0, parseInstant("2013-07-01")),
            new Transaction(30.0, parseInstant("2014-01-01")),
            new Transaction(30.0, parseInstant("2014-07-01")),
            new Transaction(30.0, parseInstant("2015-01-01")),
            new Transaction(30.0, parseInstant("2015-07-01")),
            new Transaction(30.0, parseInstant("2016-01-01")),
            new Transaction(30.0, parseInstant("2016-07-01")),
            new Transaction(30.0, parseInstant("2017-01-01")),
            new Transaction(30.0, parseInstant("2017-07-01")),
            new Transaction(30.0, parseInstant("2018-01-01")),
        ], 30.0, 123123.0, startTime, endTime, 2)
        when:
        FundComparison comparison = fundComparisonCalculatorService.calculateComparison(null, startTime, 2)
        then:
        comparison.estonianAverageReturnPercentage == 0.0326.doubleValue()
        comparison.marketAverageReturnPercentage == 0
    }

    def "it handles missing fund values"() {
        given:
        Instant startTime = parseInstant("2010-01-01")
        Instant endTime = parseInstant("2018-07-16")
        fundValueProvider.getLatestValue(_, _) >> Optional.empty()
        accountOverviewProvider.getAccountOverview(_, _, _) >> new AccountOverview([
            new Transaction(30.0, parseInstant("2010-07-01")),
            new Transaction(30.0, parseInstant("2011-01-01")),
            new Transaction(30.0, parseInstant("2011-07-01")),
            new Transaction(30.0, parseInstant("2012-01-01")),
            new Transaction(30.0, parseInstant("2012-07-01")),
            new Transaction(30.0, parseInstant("2013-01-01")),
            new Transaction(30.0, parseInstant("2013-07-01")),
            new Transaction(30.0, parseInstant("2014-01-01")),
            new Transaction(30.0, parseInstant("2014-07-01")),
            new Transaction(30.0, parseInstant("2015-01-01")),
            new Transaction(30.0, parseInstant("2015-07-01")),
            new Transaction(30.0, parseInstant("2016-01-01")),
            new Transaction(30.0, parseInstant("2016-07-01")),
            new Transaction(30.0, parseInstant("2017-01-01")),
            new Transaction(30.0, parseInstant("2017-07-01")),
            new Transaction(30.0, parseInstant("2018-01-01")),
        ], 30.0, 123123.0, startTime, endTime, 2)
        when:
        FundComparison comparison = fundComparisonCalculatorService.calculateComparison(null, startTime, 2)
        then:
        comparison.estonianAverageReturnPercentage == 0
        comparison.marketAverageReturnPercentage == 0
    }

    private static Map<String, BigDecimal> getEpiFundValuesMap() {
        return [
            "2010-01-01": 133.00,
            "2010-07-01": 127.00,
            "2011-01-01": 140.00,
            "2011-07-01": 150.00,
            "2012-01-01": 145.00,
            "2012-07-01": 150.00,
            "2013-01-01": 152.50,
            "2013-07-01": 155.00,
            "2014-01-01": 157.50,
            "2014-07-01": 160.00,
            "2015-01-01": 162.50,
            "2015-07-01": 165.00,
            "2016-01-01": 167.50,
            "2016-07-01": 170.00,
            "2017-01-01": 172.50,
            "2017-07-01": 175.00,
            "2018-01-01": 177.50,
            "2018-07-16": 180.00,
        ]
    }

    private void mockFundValues(String fund, Map<String, BigDecimal> values) {
        fundValueProvider.getLatestValue(fund, _) >> {
            String givenFund, LocalDate date ->
                Optional.of(new FundValue(null, values[date.toString()], WorldIndexValueRetriever.KEY))
        }
    }

    private static Instant parseInstant(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    private void fakeNoReturnFundValues() {
        Instant time = parseInstant("2018-06-17")
        fundValueProvider.getLatestValue(_, _) >>
            Optional.of(new FundValue(time, 1.0, WorldIndexValueRetriever.KEY))
    }
}
