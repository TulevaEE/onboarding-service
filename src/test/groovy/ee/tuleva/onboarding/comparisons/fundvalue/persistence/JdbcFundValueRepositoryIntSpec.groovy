package ee.tuleva.onboarding.comparisons.fundvalue.persistence

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.GlobalStockIndexRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.jdbc.JdbcTestUtils
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import javax.sql.DataSource
import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static java.time.LocalDate.parse

@SpringBootTest
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
            Optional<FundValue> epiLatestValue = fundValueRepository.findLastValueForFund(EpiIndex.EPI.key)
            Optional<FundValue> marketLatestValue = fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY)
        then:
            epiLatestValue.isPresent()
            epiLatestValue.get() == values[2]
            marketLatestValue.isPresent()
            marketLatestValue.get() == values[0]
    }

    def "it handles missing fund values properly"() {
        when:
            Optional<FundValue> value = fundValueRepository.findLastValueForFund(EpiIndex.EPI.key)
        then:
            !value.isPresent()
    }

    def "it can find the value closest for a time for a fund"() {
        given:
        List<FundValue> values = [
            aFundValue(EpiIndex.EPI.key, parse("1990-01-04"), 104.12345),
            aFundValue(EpiIndex.EPI.key, parse("1990-01-02"), 102.12345),
            aFundValue(EpiIndex.EPI.key, parse("1990-01-01"), 101.12345),
            aFundValue(UnionStockIndexRetriever.KEY, parse("1990-01-04"), 204.12345),
            aFundValue(UnionStockIndexRetriever.KEY, parse("1990-01-02"), 202.12345),
            aFundValue(UnionStockIndexRetriever.KEY, parse("1990-01-01"), 201.12345),
        ]
        fundValueRepository.saveAll(values)
        when:
        Optional<FundValue> epiValue = fundValueRepository.getLatestValue(EpiIndex.EPI.key, parse("1990-01-03"))
        Optional<FundValue> marketValue = fundValueRepository.getLatestValue(UnionStockIndexRetriever.KEY, parse("1990-01-06"))
        Optional<FundValue> olderValue = fundValueRepository.getLatestValue(UnionStockIndexRetriever.KEY, parse("1970-01-01"))
        then:
        epiValue.isPresent()
        marketValue.isPresent()
        !olderValue.isPresent()
        epiValue.get().value() == 102.12345
        marketValue.get().value() == 204.12345
    }

    def "it can save and update values"() {
        given:
        def oldValue = aFundValue("FOO_BAR", parse("2020-04-30"), 1.12345)
        fundValueRepository.save(oldValue)
        def newValue = aFundValue("FOO_BAR", parse("2020-04-30"), 2.12345)
        when:
        fundValueRepository.update(newValue)
        then:
        fundValueRepository.findLastValueForFund("FOO_BAR").get() == newValue
    }

    def "it can select global stock values from two tables"() {
        given:
        List<FundValue> values = [
            aFundValue(WorldIndexValueRetriever.KEY, parse("2019-12-30"), 123.45000),
            aFundValue(WorldIndexValueRetriever.KEY, parse("2019-12-31"), 124.45000),
            aFundValue(GlobalStockIndexRetriever.KEY, parse("2020-01-01"), 125.45000),
            aFundValue(GlobalStockIndexRetriever.KEY, parse("2020-01-02"), 126.45000)
        ]
        fundValueRepository.saveAll(values)
        when:
        def result = fundValueRepository.getGlobalStockValues()
        then:
        result.size() == values.size()
    }

  def "it finds the earliest date for a given fund key"() {
    given:
        String key = "SOME_FUND"
        List<FundValue> valuesWithDifferentDates = [
            aFundValue(key, parse("2020-01-01"), 101.12345),
            aFundValue(key, parse("2020-01-10"), 102.12345),
            aFundValue(key, parse("2019-12-31"), 100.12345)
        ]
        valuesWithDifferentDates.each { fundValueRepository.save(it) }

    when:
        Optional<LocalDate> earliestDate = fundValueRepository.findEarliestDateForKey(key)

    then:
        earliestDate.isPresent()
        earliestDate.get() == parse("2019-12-31")
  }

  def "it finds all earliest dates"() {
    given:
    String key = "SOME_FUND"
    String key2 = "SOME_FUND_2"
    List<FundValue> valuesWithDifferentDates = [
        aFundValue(key2, parse("2020-03-01"), 104.12345),
        aFundValue(key2, parse("2020-02-01"), 103.12345),
        aFundValue(key2, parse("2020-01-01"), 101.12345),
        aFundValue(key, parse("2020-01-01"), 101.12345),
        aFundValue(key, parse("2020-01-10"), 102.12345),
        aFundValue(key, parse("2019-12-31"), 100.12345),
    ]
    fundValueRepository.saveAll(valuesWithDifferentDates)

    when:
    Map<String, LocalDate> dates = fundValueRepository.findEarliestDates()

    then:
    dates == ["SOME_FUND": parse("2019-12-31"), "SOME_FUND_2": parse("2020-01-01")]
  }

  def "it finds values between dates for a fund"() {
    given:
    String fundKey = "TEST_FUND"
    List<FundValue> values = [
        aFundValue(fundKey, parse("2020-01-01"), 100.0),
        aFundValue(fundKey, parse("2020-01-02"), 101.0),
        aFundValue(fundKey, parse("2020-01-03"), 102.0),
        aFundValue(fundKey, parse("2020-01-04"), 103.0),
        aFundValue(fundKey, parse("2020-01-05"), 104.0),
        aFundValue("OTHER_FUND", parse("2020-01-03"), 999.0),
    ]
    fundValueRepository.saveAll(values)

    when:
    List<FundValue> result = fundValueRepository.findValuesBetweenDates(
        fundKey, parse("2020-01-02"), parse("2020-01-04")
    )

    then:
    result.size() == 3
    result[0].date() == parse("2020-01-02")
    result[0].value() == 101.0
    result[1].date() == parse("2020-01-03")
    result[1].value() == 102.0
    result[2].date() == parse("2020-01-04")
    result[2].value() == 103.0
    result.every { it.key() == fundKey }
  }

  def "it returns empty list when no values exist between dates"() {
    given:
    String fundKey = "TEST_FUND"

    when:
    List<FundValue> result = fundValueRepository.findValuesBetweenDates(
        fundKey, parse("2020-01-01"), parse("2020-01-31")
    )

    then:
    result.isEmpty()
  }

  def "it saves and retrieves provider and updatedAt fields"() {
    given:
    def now = Instant.now()
    def fundValue = new FundValue("TEST_KEY", parse("2020-01-01"), 100.0, "EODHD", now)

    when:
    fundValueRepository.save(fundValue)
    def result = fundValueRepository.findLastValueForFund("TEST_KEY")

    then:
    result.isPresent()
    result.get().key() == "TEST_KEY"
    result.get().value() == 100.0
    result.get().provider() == "EODHD"
    result.get().updatedAt() != null
  }

  def "it updates provider and updatedAt fields"() {
    given:
    def now = Instant.now()
    fundValueRepository.save(new FundValue("UPDATE_KEY", parse("2020-01-01"), 100.0, "YAHOO", now))

    when:
    def newNow = Instant.now()
    fundValueRepository.update(new FundValue("UPDATE_KEY", parse("2020-01-01"), 200.0, "EODHD", newNow))
    def result = fundValueRepository.findLastValueForFund("UPDATE_KEY")

    then:
    result.isPresent()
    result.get().value() == 200.0
    result.get().provider() == "EODHD"
  }

  private static List<FundValue> getFakeFundValues() {
        def today = LocalDate.now()
        def yesterday = LocalDate.now().minusDays(1)
        return [
            aFundValue(UnionStockIndexRetriever.KEY, today, 100.12345),
            aFundValue(UnionStockIndexRetriever.KEY, yesterday, 10.12345),
            aFundValue(EpiIndex.EPI.key, today, 200.12345),
            aFundValue(EpiIndex.EPI.key, yesterday, 20.12345),
        ]
    }
}
