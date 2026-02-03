package ee.tuleva.onboarding.comparisons.fundvalue.persistence

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.GlobalStockIndexRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static java.time.LocalDate.parse

@SpringBootTest
@Transactional
class JdbcFundValueRepositoryIntSpec extends Specification {

    @Autowired
    JdbcFundValueRepository fundValueRepository

    def "it can persist a bunch of fund values"() {
        given:
            def uniqueKey1 = "PERSIST_TEST_" + UUID.randomUUID()
            def uniqueKey2 = "PERSIST_TEST_" + UUID.randomUUID()
            def today = LocalDate.now()
            def yesterday = LocalDate.now().minusDays(1)
            List<FundValue> values = [
                aFundValue(uniqueKey1, today, 100.12345),
                aFundValue(uniqueKey1, yesterday, 10.12345),
                aFundValue(uniqueKey2, today, 200.12345),
                aFundValue(uniqueKey2, yesterday, 20.12345),
            ]
        when:
            def savedValues = fundValueRepository.saveAll(values)
        then:
            savedValues.size() == values.size()
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
    String key = "EARLIEST_DATES_TEST_" + UUID.randomUUID()
    String key2 = "EARLIEST_DATES_TEST_" + UUID.randomUUID()
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
    dates[key] == parse("2019-12-31")
    dates[key2] == parse("2020-01-01")
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

  def "save returns saved value for new entries"() {
    given:
    def newValue = aFundValue("NEW_KEY", parse("2020-01-01"), 100.0, "YAHOO")

    when:
    def result = fundValueRepository.save(newValue)

    then:
    result.isPresent()
    result.get() == newValue
  }

  def "save returns empty for duplicate key-date combinations"() {
    given:
    def originalValue = aFundValue("IMMUTABLE_KEY", parse("2020-01-01"), 100.0, "YAHOO")
    fundValueRepository.save(originalValue)

    when:
    def duplicateValue = aFundValue("IMMUTABLE_KEY", parse("2020-01-01"), 999.0, "DIFFERENT_PROVIDER")
    def result = fundValueRepository.save(duplicateValue)

    then:
    result.isEmpty()
    fundValueRepository.findLastValueForFund("IMMUTABLE_KEY").get().value() == 100.0
  }

  def "saveAll returns only actually saved values"() {
    given:
    def existingValue = aFundValue("BATCH_KEY", parse("2020-01-01"), 100.0, "YAHOO")
    fundValueRepository.save(existingValue)

    when:
    def valuesToSave = [
        aFundValue("BATCH_KEY", parse("2020-01-01"), 999.0, "DIFFERENT"),
        aFundValue("BATCH_KEY", parse("2020-01-02"), 200.0, "YAHOO")
    ]
    def savedValues = fundValueRepository.saveAll(valuesToSave)

    then:
    savedValues.size() == 1
    savedValues[0].date() == parse("2020-01-02")
    savedValues[0].value() == 200.0
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
