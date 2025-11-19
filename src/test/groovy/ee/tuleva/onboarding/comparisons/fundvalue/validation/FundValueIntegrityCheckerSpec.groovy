package ee.tuleva.onboarding.comparisons.fundvalue.validation

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.NAVCheckValueRetriever
import spock.lang.Specification

import java.time.LocalDate

class FundValueIntegrityCheckerSpec extends Specification {

    NAVCheckValueRetriever navCheckValueRetriever = Stub()
    FundValueRepository fundValueRepository = Stub()

    FundValueIntegrityChecker checker = new FundValueIntegrityChecker(
            navCheckValueRetriever,
            fundValueRepository
    )

    def "should not report discrepancy when values differ only after 5 decimal places"() {
        given:
        String fundTicker = "0P000152G5.F"
        LocalDate date = LocalDate.of(2018, 1, 22)

        FundValue dbValue = new FundValue(fundTicker, date, new BigDecimal("14.01900"))
        FundValue yahooValue = new FundValue(fundTicker, date, new BigDecimal("14.019000053405762"))

        fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
        navCheckValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

        when:
        IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

        then:
        result.getDiscrepancies().isEmpty()
        result.getMissingData().isEmpty()
        result.getOrphanedData().isEmpty()
        !result.hasIssues()
    }

    def "should not report discrepancy for exact case from production logs"() {
        given:
        String fundTicker = "0P0000YXER.F"
        LocalDate date = LocalDate.of(2024, 1, 30)

        FundValue dbValue = new FundValue(fundTicker, date, new BigDecimal("110.99000"))
        FundValue yahooValue = new FundValue(fundTicker, date, new BigDecimal("110.98999786376953"))

        fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
        navCheckValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

        when:
        IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

        then:
        result.getDiscrepancies().isEmpty()
        !result.hasIssues()
    }

    def "should report discrepancy when values differ within 5 decimal places"() {
        given:
        String fundTicker = "0P000152G5.F"
        LocalDate date = LocalDate.of(2018, 1, 22)

        FundValue dbValue = new FundValue(fundTicker, date, new BigDecimal("14.01900"))
        FundValue yahooValue = new FundValue(fundTicker, date, new BigDecimal("14.01901"))

        fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
        navCheckValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

        when:
        IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

        then:
        result.getDiscrepancies().size() == 1
        result.hasIssues()

        IntegrityCheckResult.Discrepancy discrepancy = result.getDiscrepancies().first()
        discrepancy.fundTicker() == fundTicker
        discrepancy.date() == date
        discrepancy.dbValue() == new BigDecimal("14.01900")
        discrepancy.yahooValue() == new BigDecimal("14.01901")
        discrepancy.difference() == new BigDecimal("0.00001")
    }

    def "should report missing data when Yahoo has value but database does not"() {
        given:
        String fundTicker = "TEST.F"
        LocalDate date = LocalDate.of(2024, 1, 1)

        FundValue yahooValue = new FundValue(fundTicker, date, new BigDecimal("100.12345"))

        fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> []
        navCheckValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

        when:
        IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

        then:
        result.getMissingData().size() == 1
        result.hasIssues()

        IntegrityCheckResult.MissingData missing = result.getMissingData().first()
        missing.fundTicker() == fundTicker
        missing.date() == date
        missing.yahooValue() == new BigDecimal("100.12345")
    }

    def "should report orphaned data when database has value but Yahoo does not"() {
        given:
        String fundTicker = "TEST.F"
        LocalDate date = LocalDate.of(2024, 1, 1)

        FundValue dbValue = new FundValue(fundTicker, date, new BigDecimal("100.12345"))

        fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
        navCheckValueRetriever.retrieveValuesForRange(date, date) >> []

        when:
        IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

        then:
        result.getOrphanedData().size() == 1
        result.hasIssues()

        IntegrityCheckResult.OrphanedData orphaned = result.getOrphanedData().first()
        orphaned.fundTicker() == fundTicker
        orphaned.date() == date
    }

    def "should handle multiple issues in single check"() {
        given:
        String fundTicker = "TEST.F"
        LocalDate date1 = LocalDate.of(2024, 1, 1)
        LocalDate date2 = LocalDate.of(2024, 1, 2)
        LocalDate date3 = LocalDate.of(2024, 1, 3)

        FundValue dbValue1 = new FundValue(fundTicker, date1, new BigDecimal("100.00000"))
        FundValue dbValue3 = new FundValue(fundTicker, date3, new BigDecimal("102.00000"))

        FundValue yahooValue1 = new FundValue(fundTicker, date1, new BigDecimal("100.00001"))
        FundValue yahooValue2 = new FundValue(fundTicker, date2, new BigDecimal("101.00000"))

        fundValueRepository.findValuesBetweenDates(fundTicker, date1, date3) >> [dbValue1, dbValue3]
        navCheckValueRetriever.retrieveValuesForRange(date1, date3) >> [yahooValue1, yahooValue2]

        when:
        IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date1, date3)

        then:
        result.getDiscrepancies().size() == 1  // date1 has discrepancy
        result.getMissingData().size() == 1    // date2 missing in DB
        result.getOrphanedData().size() == 1   // date3 missing in Yahoo
        result.hasIssues()
    }
}
