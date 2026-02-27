package ee.tuleva.onboarding.comparisons.fundvalue.validation

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.validation.IntegrityCheckResult.Severity

class FundValueIntegrityCheckerSpec extends Specification {

  YahooFundValueRetriever yahooFundValueRetriever = Stub()
  FundValueRepository fundValueRepository = Stub()

  FundValueIntegrityChecker checker = new FundValueIntegrityChecker(
      yahooFundValueRetriever,
      fundValueRepository
  )

  def "should not report discrepancy when values differ only after 5 decimal places"() {
    given:
    String fundTicker = "0P000152G5.F"
    LocalDate date = LocalDate.of(2018, 1, 22)

    FundValue dbValue = aFundValue(fundTicker, date, 14.01900)
    FundValue yahooValue = aFundValue(fundTicker, date, 14.019000053405762)

    fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
    yahooFundValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

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

    FundValue dbValue = aFundValue(fundTicker, date, 110.99000)
    FundValue yahooValue = aFundValue(fundTicker, date, 110.98999786376953)

    fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
    yahooFundValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

    when:
    IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

    then:
    result.getDiscrepancies().isEmpty()
    !result.hasIssues()
  }

  def "should report discrepancy when values differ by more than 0.0001%"() {
    given:
    String fundTicker = "0P000152G5.F"
    LocalDate date = LocalDate.of(2018, 1, 22)

    FundValue dbValue = aFundValue(fundTicker, date, 100.00000)
    FundValue yahooValue = aFundValue(fundTicker, date, 100.00020)

    fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
    yahooFundValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

    when:
    IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

    then:
    result.getDiscrepancies().size() == 1
    result.hasIssues()

    IntegrityCheckResult.Discrepancy discrepancy = result.getDiscrepancies().first()
    discrepancy.fundTicker() == fundTicker
    discrepancy.date() == date
    discrepancy.percentageDifference() > 0.0001
  }

  def "should report missing data when Yahoo has value but database does not"() {
    given:
    String fundTicker = "TEST.F"
    LocalDate date = LocalDate.of(2024, 1, 1)

    FundValue yahooValue = aFundValue(fundTicker, date, 100.12345)

    fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> []
    yahooFundValueRetriever.retrieveValuesForRange(date, date) >> [yahooValue]

    when:
    IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date, date)

    then:
    result.getMissingData().size() == 1
    result.hasIssues()

    IntegrityCheckResult.MissingData missing = result.getMissingData().first()
    missing.fundTicker() == fundTicker
    missing.date() == date
    missing.referenceValue() == 100.12345
  }

  def "should report orphaned data when database has value but Yahoo does not"() {
    given:
    String fundTicker = "TEST.F"
    LocalDate date = LocalDate.of(2024, 1, 1)

    FundValue dbValue = aFundValue(fundTicker, date, 100.12345)

    fundValueRepository.findValuesBetweenDates(fundTicker, date, date) >> [dbValue]
    yahooFundValueRetriever.retrieveValuesForRange(date, date) >> []

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

    FundValue dbValue1 = aFundValue(fundTicker, date1, 100.00000)
    FundValue dbValue3 = aFundValue(fundTicker, date3, 102.00000)

    FundValue yahooValue1 = aFundValue(fundTicker, date1, 100.00020)
    FundValue yahooValue2 = aFundValue(fundTicker, date2, 101.00000)

    fundValueRepository.findValuesBetweenDates(fundTicker, date1, date3) >> [dbValue1, dbValue3]
    yahooFundValueRetriever.retrieveValuesForRange(date1, date3) >> [yahooValue1, yahooValue2]

    when:
    IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, date1, date3)

    then:
    result.getDiscrepancies().size() == 1  // date1 has discrepancy (0.0002% > 0.0001%)
    result.getMissingData().size() == 1    // date2 missing in DB
    result.getOrphanedData().size() == 1   // date3 missing in Yahoo
    result.hasIssues()
  }

  def "should exclude today's date from integrity check"() {
    given:
    String fundTicker = "TEST.F"
    LocalDate today = LocalDate.now()
    LocalDate yesterday = today.minusDays(1)

    FundValue yesterdayValue = aFundValue(fundTicker, yesterday, 99.00000)

    fundValueRepository.findValuesBetweenDates(fundTicker, _, yesterday) >> [yesterdayValue]
    yahooFundValueRetriever.retrieveValuesForRange(_, yesterday) >> [yesterdayValue]

    when:
    IntegrityCheckResult result = checker.verifyFundDataIntegrity(fundTicker, yesterday, yesterday)

    then:
    result.getDiscrepancies().isEmpty()
    result.getMissingData().isEmpty()
    result.getOrphanedData().isEmpty()
    !result.hasIssues()
  }

  // Cross-provider integrity tests with Exchange/EODHD as anchor

  def "should report CRITICAL when Exchange and EODHD values differ by more than 0.001%"() {
    given:
    def ticker = FundTicker.ISHARES_USA_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def xetraKey = ticker.isin + ".XETR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)
    FundValue xetraValue = aFundValue(xetraKey, date, 100.002)
    FundValue yahooValue = aFundValue(ticker.yahooTicker, date, 100.00)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(xetraKey, date, date) >> [xetraValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> [yahooValue]

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.size() == 1
    criticalDiscrepancies[0].date() == date
    criticalDiscrepancies[0].percentageDifference() > 0.001
    criticalDiscrepancies[0].comparisonDescription() == "Exchange vs EODHD"
  }

  def "should report INFO when EODHD and Yahoo values differ by more than 0.001%"() {
    given:
    def ticker = FundTicker.ISHARES_USA_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def xetraKey = ticker.isin + ".XETR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)
    FundValue xetraValue = aFundValue(xetraKey, date, 100.00)
    FundValue yahooValue = aFundValue(ticker.yahooTicker, date, 100.002)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(xetraKey, date, date) >> [xetraValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> [yahooValue]

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def infoDiscrepancies = discrepancies.findAll { it.severity() == Severity.INFO }
    infoDiscrepancies.size() == 1
    infoDiscrepancies[0].date() == date
    infoDiscrepancies[0].percentageDifference() > 0.001
    infoDiscrepancies[0].comparisonDescription() == "EODHD vs Yahoo"
  }

  def "should not report discrepancy when Exchange and EODHD values differ by less than 0.001%"() {
    given:
    def ticker = FundTicker.ISHARES_USA_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def xetraKey = ticker.isin + ".XETR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00000)
    FundValue xetraValue = aFundValue(xetraKey, date, 100.00050)
    FundValue yahooValue = aFundValue(ticker.yahooTicker, date, 100.00000)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(xetraKey, date, date) >> [xetraValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> [yahooValue]

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.isEmpty()
  }

  def "should skip cross-provider comparison when only one provider has data"() {
    given:
    def ticker = FundTicker.ISHARES_USA_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def xetraKey = ticker.isin + ".XETR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(xetraKey, date, date) >> []
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> []

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    discrepancies.isEmpty()
  }

  def "should check Deutsche Börse as anchor for Xetra-traded ETFs"() {
    given:
    def ticker = FundTicker.ISHARES_USA_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def xetraKey = ticker.isin + ".XETR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)
    FundValue xetraValue = aFundValue(xetraKey, date, 100.002)
    FundValue yahooValue = aFundValue(ticker.yahooTicker, date, 100.00)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(xetraKey, date, date) >> [xetraValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> [yahooValue]

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.size() == 1
    criticalDiscrepancies[0].fundTicker().contains("Deutsche Börse")
    criticalDiscrepancies[0].date() == date
  }

  def "should check Euronext as anchor for Paris-traded ETFs"() {
    given:
    def ticker = FundTicker.AMUNDI_USA_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def euronextKey = ticker.isin + ".XPAR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)
    FundValue euronextValue = aFundValue(euronextKey, date, 100.002)
    FundValue yahooValue = aFundValue(ticker.yahooTicker, date, 100.00)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(euronextKey, date, date) >> [euronextValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> [yahooValue]

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.size() == 1
    criticalDiscrepancies[0].fundTicker().contains("Euronext")
    criticalDiscrepancies[0].date() == date
  }

  def "should report CRITICAL when BlackRock and Morningstar differ after rounding to 2 decimals"() {
    given:
    def ticker = FundTicker.ISHARES_DEVELOPED_WORLD_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def blackrockKey = ticker.isin + ".BLACKROCK"
    def morningstarKey = ticker.isin + ".MORNINGSTAR"

    FundValue blackrockValue = aFundValue(blackrockKey, date, 34.5732)
    FundValue morningstarValue = aFundValue(morningstarKey, date, 34.56)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> []
    fundValueRepository.findValuesBetweenDates(blackrockKey, date, date) >> [blackrockValue]
    fundValueRepository.findValuesBetweenDates(morningstarKey, date, date) >> [morningstarValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> []

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.size() == 1
    criticalDiscrepancies[0].comparisonDescription() == "BlackRock vs Morningstar"
  }

  def "should not report discrepancy when BlackRock and Morningstar match at 2 decimal places"() {
    given:
    def ticker = FundTicker.ISHARES_DEVELOPED_WORLD_ESG_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def blackrockKey = ticker.isin + ".BLACKROCK"
    def morningstarKey = ticker.isin + ".MORNINGSTAR"

    FundValue blackrockValue = aFundValue(blackrockKey, date, 34.5632)
    FundValue morningstarValue = aFundValue(morningstarKey, date, 34.56)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> []
    fundValueRepository.findValuesBetweenDates(blackrockKey, date, date) >> [blackrockValue]
    fundValueRepository.findValuesBetweenDates(morningstarKey, date, date) >> [morningstarValue]
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> []

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.isEmpty()
  }

  def "should not check Xetra for non-Xetra ETFs"() {
    given:
    def ticker = FundTicker.AMUNDI_USA_SCREENED
    LocalDate date = LocalDate.of(2024, 1, 15)
    def euronextKey = ticker.isin + ".XPAR"

    FundValue eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)
    FundValue yahooValue = aFundValue(ticker.yahooTicker, date, 100.00)

    fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, date, date) >> [eodhdValue]
    fundValueRepository.findValuesBetweenDates(euronextKey, date, date) >> []
    fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, date, date) >> [yahooValue]

    when:
    def discrepancies = checker.checkCrossProviderIntegrity(ticker, date, date)

    then:
    def criticalDiscrepancies = discrepancies.findAll { it.severity() == Severity.CRITICAL }
    criticalDiscrepancies.isEmpty()
  }

  def "should log summary table with all tickers"() {
    given:
    LocalDate date = LocalDate.of(2024, 1, 15)

    yahooFundValueRetriever.retrieveValuesForRange(_, _) >> FundTicker.values().collect {
      aFundValue(it.yahooTicker, date, 100.00)
    }

    for (ticker in FundTicker.values()) {
      def eodhdValue = aFundValue(ticker.eodhdTicker, date, 100.00)
      def yahooValue = aFundValue(ticker.yahooTicker, date, 100.00)

      fundValueRepository.findValuesBetweenDates(ticker.yahooTicker, _, _) >> [yahooValue]
      fundValueRepository.findValuesBetweenDates(ticker.eodhdTicker, _, _) >> [eodhdValue]

      ticker.getXetraStorageKey().ifPresent { xetraKey ->
        fundValueRepository.findValuesBetweenDates(xetraKey, _, _) >> [aFundValue(xetraKey, date, 100.00)]
      }
      ticker.getEuronextParisStorageKey().ifPresent { euronextKey ->
        fundValueRepository.findValuesBetweenDates(euronextKey, _, _) >> [aFundValue(euronextKey, date, 100.00)]
      }
      ticker.getBlackrockStorageKey().ifPresent { blackrockKey ->
        fundValueRepository.findValuesBetweenDates(blackrockKey, _, _) >> [aFundValue(blackrockKey, date, 100.00)]
      }
      ticker.getMorningstarStorageKey().ifPresent { morningstarKey ->
        fundValueRepository.findValuesBetweenDates(morningstarKey, _, _) >> [aFundValue(morningstarKey, date, 100.00)]
      }
    }

    when:
    checker.performIntegrityCheck()

    then:
    noExceptionThrown()
  }
}
