package ee.tuleva.onboarding.comparisons.fundvalue.persistence

import ee.tuleva.onboarding.OnboardingServiceApplication
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.GlobalStockIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.jdbc.JdbcTestUtils
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import javax.sql.DataSource
import java.time.LocalDate

import static java.time.LocalDate.parse

@SpringBootTest(classes = OnboardingServiceApplication)
@ContextConfiguration
@Transactional
class JdbcFundValueRepositoryIntSpec extends Specification {

    JdbcTemplate jdbcTemplate

    @Autowired
    void setDataSource(DataSource dataSource) {
        jdbcTemplate = new JdbcTemplate(dataSource)
    }

    @Autowired
    JdbcFundValueRepository fundValueRepository

    def "it can persist a bunch of fund values"() {
        given:
            List<FundValue> values = getFakeFundValues()
        when:
            fundValueRepository.saveAll(values)
        then:
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "index_values") == values.size()
    }

    def "it can retrieve fund values by last time and fund"() {
        given:
            List<FundValue> values = getFakeFundValues()
            fundValueRepository.saveAll(values)
        when:
            Optional<FundValue> epiLatestValue = fundValueRepository.findLastValueForFund(EPIFundValueRetriever.KEY)
            Optional<FundValue> marketLatestValue = fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY)
        then:
            epiLatestValue.isPresent()
            valuesEqual(epiLatestValue.get(), values[2])
            marketLatestValue.isPresent()
            valuesEqual(marketLatestValue.get(), values[0])
    }

    def "it handles missing fund values properly"() {
        when:
            Optional<FundValue> value = fundValueRepository.findLastValueForFund(EPIFundValueRetriever.KEY)
        then:
            !value.isPresent()
    }

    def "it can find the value closest for a time for a fund"() {
        given:
        List<FundValue> values = [
            new FundValue(EPIFundValueRetriever.KEY, parse("1990-01-04"), 104.12345),
            new FundValue(EPIFundValueRetriever.KEY, parse("1990-01-02"), 102.12345),
            new FundValue(EPIFundValueRetriever.KEY, parse("1990-01-01"), 101.12345),
            new FundValue(UnionStockIndexRetriever.KEY, parse("1990-01-04"), 204.12345),
            new FundValue(UnionStockIndexRetriever.KEY, parse("1990-01-02"), 202.12345),
            new FundValue(UnionStockIndexRetriever.KEY, parse("1990-01-01"), 201.12345),
        ]
        fundValueRepository.saveAll(values)
        when:
        Optional<FundValue> epiValue = fundValueRepository.getLatestValue(EPIFundValueRetriever.KEY, parse("1990-01-03"))
        Optional<FundValue> marketValue = fundValueRepository.getLatestValue(UnionStockIndexRetriever.KEY, parse("1990-01-06"))
        Optional<FundValue> olderValue = fundValueRepository.getLatestValue(UnionStockIndexRetriever.KEY, parse("1970-01-01"))
        then:
        epiValue.isPresent()
        marketValue.isPresent()
        !olderValue.isPresent()
        epiValue.get().getValue() == 102.12345
        marketValue.get().getValue() == 204.12345
    }

    def "it can save and update values"() {
        given:
        def oldValue = new FundValue("FOO_BAR", parse("2020-04-30"), 1.12345)
        fundValueRepository.save(oldValue)
        def newValue = new FundValue("FOO_BAR", parse("2020-04-30"), 2.12345)
        when:
        fundValueRepository.update(newValue)
        then:
        fundValueRepository.findLastValueForFund("FOO_BAR").get() == newValue
    }

    def "it can select global stock values from two tables"() {
        given:
        List<FundValue> values = [
            new FundValue(WorldIndexValueRetriever.KEY, parse("2019-12-30"), 123.45000),
            new FundValue(WorldIndexValueRetriever.KEY, parse("2019-12-31"), 124.45000),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-01-01"), 125.45000),
            new FundValue(GlobalStockIndexRetriever.KEY, parse("2020-01-02"), 126.45000)
        ]
        fundValueRepository.saveAll(values)
        when:
        def result = fundValueRepository.getGlobalStockValues()
        then:
        result.containsAll(values)
    }

  def "it finds active fund keys starting with EE and recent entries"() {
    given:
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3)
        LocalDate fourMonthsAgo = LocalDate.now().minusMonths(4)
        List<FundValue> recentValues = [
            new FundValue("EE_FUND_0", fourMonthsAgo, 10.12345),
            new FundValue("EE_FUND_1", threeMonthsAgo, 100.12345),
            new FundValue("EE_FUND_2", LocalDate.now(), 200.12345)
        ]
        recentValues.each { fundValueRepository.save(it) }
        fundValueRepository.save(new FundValue("NON_EE_FUND",  LocalDate.now(), 300.12345))

    when:
        List<String> activeKeys = fundValueRepository.findActiveFundKeys()

    then:
        activeKeys.size() == 2
        activeKeys.containsAll(["EE_FUND_1", "EE_FUND_2"])
  }

  def "it finds the earliest date for a given fund key"() {
    given:
        String key = "SOME_FUND"
        List<FundValue> valuesWithDifferentDates = [
            new FundValue(key, parse("2020-01-01"), 101.12345),
            new FundValue(key, parse("2020-01-10"), 102.12345),
            new FundValue(key, parse("2019-12-31"), 100.12345)
        ]
        valuesWithDifferentDates.each { fundValueRepository.save(it) }

    when:
        Optional<LocalDate> earliestDate = fundValueRepository.findEarliestDateForKey(key)

    then:
        earliestDate.isPresent()
        earliestDate.get() == parse("2019-12-31")
  }

  private static List<FundValue> getFakeFundValues() {
        def today = LocalDate.now()
        def yesterday = LocalDate.now().minusDays(1)
        return [
            new FundValue(UnionStockIndexRetriever.KEY, today, 100.12345),
            new FundValue(UnionStockIndexRetriever.KEY, yesterday, 10.12345),
            new FundValue(EPIFundValueRetriever.KEY, today, 200.12345),
            new FundValue(EPIFundValueRetriever.KEY, yesterday, 20.12345),
        ]
    }

    private static boolean valuesEqual(FundValue one, FundValue two) {
        return one.date == two.date && one.comparisonFund == two.comparisonFund && one.value == two.value
    }
}
