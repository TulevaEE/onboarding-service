package ee.tuleva.onboarding.mandate.statistics

import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

class FundValueStatisticsSpec extends Specification {

    def "OnCreate: On creation set date truncated to today"() {
        when:
        FundValueStatistics fundValueStatistics = FundValueStatistics.builder().build()
        fundValueStatistics.onCreate()
        then:
        fundValueStatistics.createdDate == Instant.now().truncatedTo(ChronoUnit.DAYS)
    }

}
