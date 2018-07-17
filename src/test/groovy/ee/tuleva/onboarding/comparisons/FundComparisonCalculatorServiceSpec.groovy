package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import spock.lang.Specification

import java.text.SimpleDateFormat
import java.time.Instant

class FundComparisonCalculatorServiceSpec extends Specification {

    AccountOverviewProvider accountOverviewProvider
    FundValueProvider estonianAverageValueProvider
    FundValueProvider marketAverageValueProvider

    FundComparisonCalculatorService fundComparisonCalculatorService

    void setup() {
        accountOverviewProvider = Mock(AccountOverviewProvider)
        estonianAverageValueProvider = Mock(FundValueProvider)
        marketAverageValueProvider = Mock(FundValueProvider)

        fundComparisonCalculatorService = new FundComparisonCalculatorService(
                accountOverviewProvider,
                estonianAverageValueProvider,
                marketAverageValueProvider
        )
    }

    def "it uses an account overview for the given person and start time" () {
        given:
            Person person = Mock(Person)
            Instant startTime = parseInstant("2018-06-17")
            Instant endTime = parseInstant("2018-06-18")
            fakeNoReturnFundValues()
        when:
            fundComparisonCalculatorService.calculateComparison(person, startTime)
        then:
            1 * accountOverviewProvider.getAccountOverview(person, startTime) >> new AccountOverview([
                    new Transaction(-100, startTime),
                    new Transaction(-100, startTime),
                    new Transaction(200, endTime),
            ], 0, 200, startTime, endTime)
    }

    def "it successfully calculates a return of 0%" () {
        given:
            Instant startTime = parseInstant("2018-06-17")
            Instant endTime = parseInstant("2018-06-18")
            fakeNoReturnFundValues()
            accountOverviewProvider.getAccountOverview(_, _) >> new AccountOverview([
                    new Transaction(-100, startTime),
                    new Transaction(-100, startTime),
                    new Transaction(200, endTime),
            ], 0, 200, startTime, endTime)
        when:
            FundComparison comparison = fundComparisonCalculatorService.calculateComparison(null, startTime)
        then:
            comparison.actualReturnPercentage == 0
            comparison.estonianAverageReturnPercentage == 0
            comparison.marketAverageReturnPercentage == 0
    }

    def "it correctly calculates actual return"() {
        given:
            fakeNoReturnFundValues()

            Instant startTime = parseInstant("2010-01-01")
            Instant endTime = parseInstant("2018-07-18")
            accountOverviewProvider.getAccountOverview(_, _) >> new AccountOverview([
                    new Transaction(-30, parseInstant("2010-01-01")),
                    new Transaction(-30, parseInstant("2010-07-01")),
                    new Transaction(-30, parseInstant("2011-01-01")),
                    new Transaction(-30, parseInstant("2011-07-01")),
                    new Transaction(-30, parseInstant("2012-01-01")),
                    new Transaction(-30, parseInstant("2012-07-01")),
                    new Transaction(-30, parseInstant("2013-01-01")),
                    new Transaction(-30, parseInstant("2013-07-01")),
                    new Transaction(-30, parseInstant("2014-01-01")),
                    new Transaction(-30, parseInstant("2014-07-01")),
                    new Transaction(-30, parseInstant("2015-01-01")),
                    new Transaction(-30, parseInstant("2015-07-01")),
                    new Transaction(-30, parseInstant("2016-01-01")),
                    new Transaction(-30, parseInstant("2016-07-01")),
                    new Transaction(-30, parseInstant("2017-01-01")),
                    new Transaction(-30, parseInstant("2017-07-01")),
                    new Transaction(-30, parseInstant("2018-01-01")),
                    new Transaction(620, parseInstant("2018-07-18")),
            ], 0, 620, startTime, endTime)
        when:
            FundComparison comparison = fundComparisonCalculatorService.calculateComparison(null, startTime)
        then:
            comparison.actualReturnPercentage == 0.0427.doubleValue()
            comparison.estonianAverageReturnPercentage == 0
            comparison.marketAverageReturnPercentage == 0
    }

    def "it correctly calculates simulated return using a different fund"() {
        given:
            Instant startTime = parseInstant("2010-01-01")
            Instant endTime = parseInstant("2018-07-16")
            Map<String, BigDecimal> fundValues = getEpiFundValuesMap()
            mockFundValues(estonianAverageValueProvider, fundValues)
            marketAverageValueProvider.getFundValueClosestToTime(_) >> { Instant time -> new FundValue(time, 123.0) }
            accountOverviewProvider.getAccountOverview(_, _) >> new AccountOverview([
                    new Transaction(-30, parseInstant("2010-01-01")),
                    new Transaction(-30, parseInstant("2010-07-01")),
                    new Transaction(-30, parseInstant("2011-01-01")),
                    new Transaction(-30, parseInstant("2011-07-01")),
                    new Transaction(-30, parseInstant("2012-01-01")),
                    new Transaction(-30, parseInstant("2012-07-01")),
                    new Transaction(-30, parseInstant("2013-01-01")),
                    new Transaction(-30, parseInstant("2013-07-01")),
                    new Transaction(-30, parseInstant("2014-01-01")),
                    new Transaction(-30, parseInstant("2014-07-01")),
                    new Transaction(-30, parseInstant("2015-01-01")),
                    new Transaction(-30, parseInstant("2015-07-01")),
                    new Transaction(-30, parseInstant("2016-01-01")),
                    new Transaction(-30, parseInstant("2016-07-01")),
                    new Transaction(-30, parseInstant("2017-01-01")),
                    new Transaction(-30, parseInstant("2017-07-01")),
                    new Transaction(-30, parseInstant("2018-01-01")),
                    new Transaction(123123, parseInstant("2018-07-16")), // making sure this is ignored
            ], 0, 123123, startTime, endTime)
        when:
            FundComparison comparison = fundComparisonCalculatorService.calculateComparison(null, startTime)
        then:
            comparison.estonianAverageReturnPercentage == 0.0326.doubleValue()
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

    private void mockFundValues(FundValueProvider provider, Map<String, BigDecimal> values) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
        provider.getFundValueClosestToTime(_) >> {
            Instant time -> new FundValue(time, values[format.format(Date.from(time))])
        }
    }

    private static Instant parseInstant(String dateFormat) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
        Date parsed = format.parse(dateFormat)
        return parsed.toInstant()
    }

    private void fakeNoReturnFundValues() {
        Instant time = parseInstant("2018-06-17")
        estonianAverageValueProvider.getFundValueClosestToTime(_) >> new FundValue(time, 1)
        marketAverageValueProvider.getFundValueClosestToTime(_) >> new FundValue(time, 1)
    }
}
