package ee.tuleva.onboarding.comparisons.fundvalue.validation

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.NAVCheckValueRetriever
import spock.lang.Specification

import java.time.LocalDate


class FundValueIntegrityCheckerSpec extends Specification {

    NAVCheckValueRetriever navCheckValueRetriever = Mock()
    FundValueRepository fundValueRepository = Mock()

    FundValueIntegrityChecker checker = new FundValueIntegrityChecker(
            navCheckValueRetriever,
            fundValueRepository
    )

    def "should not report discrepancy when values differ only after 5 decimal places"() {
        given:
        String fundTicker = "0P000152G5.F"
        LocalDate startDate = LocalDate.of(2018, 1, 22)
        LocalDate endDate = LocalDate.of(2018, 1, 22)

        FundValue dbValue = new FundValue(fundTicker, startDate, new BigDecimal("14.01900"))
        fundValueRepository.findValuesBetweenDates(fundTicker, startDate, endDate) >> [dbValue]

        FundValue yahooValue = new FundValue(fundTicker, startDate, new BigDecimal("14.019000053405762"))
        navCheckValueRetriever.retrieveValuesForRange(startDate, endDate) >> [yahooValue]

        when:
        checker.verifyFundDataIntegrity(fundTicker, startDate, endDate)

        then:
        noExceptionThrown()
    }

    def "should report discrepancy when values differ within 5 decimal places"() {
        given:
        String fundTicker = "0P000152G5.F"
        LocalDate startDate = LocalDate.of(2018, 1, 22)
        LocalDate endDate = LocalDate.of(2018, 1, 22)

        FundValue dbValue = new FundValue(fundTicker, startDate, new BigDecimal("14.01900"))
        fundValueRepository.findValuesBetweenDates(fundTicker, startDate, endDate) >> [dbValue]

        FundValue yahooValue = new FundValue(fundTicker, startDate, new BigDecimal("14.01901"))
        navCheckValueRetriever.retrieveValuesForRange(startDate, endDate) >> [yahooValue]

        when:
        checker.verifyFundDataIntegrity(fundTicker, startDate, endDate)

        then:
        noExceptionThrown()
    }

    def "should report missing data when Yahoo has value but database does not"() {
        given:
        String fundTicker = "TEST.F"
        LocalDate startDate = LocalDate.of(2024, 1, 1)
        LocalDate endDate = LocalDate.of(2024, 1, 2)

        fundValueRepository.findValuesBetweenDates(fundTicker, startDate, endDate) >> []

        FundValue yahooValue = new FundValue(fundTicker, startDate, new BigDecimal("100.12345"))
        navCheckValueRetriever.retrieveValuesForRange(startDate, endDate) >> [yahooValue]

        when:
        checker.verifyFundDataIntegrity(fundTicker, startDate, endDate)

        then:
        noExceptionThrown()
    }

    def "should report orphaned data when database has value but Yahoo does not"() {
        given:
        String fundTicker = "TEST.F"
        LocalDate startDate = LocalDate.of(2024, 1, 1)
        LocalDate endDate = LocalDate.of(2024, 1, 2)

        FundValue dbValue = new FundValue(fundTicker, startDate, new BigDecimal("100.12345"))
        fundValueRepository.findValuesBetweenDates(fundTicker, startDate, endDate) >> [dbValue]

        navCheckValueRetriever.retrieveValuesForRange(startDate, endDate) >> []

        when:
        checker.verifyFundDataIntegrity(fundTicker, startDate, endDate)

        then:
        noExceptionThrown()
    }
}
