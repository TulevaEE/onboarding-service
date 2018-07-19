package ee.tuleva.onboarding.comparisons.fundvalue.persistence

import ee.tuleva.onboarding.OnboardingServiceApplication
import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.jdbc.JdbcTestUtils
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import javax.sql.DataSource
import java.text.SimpleDateFormat
import java.time.Instant

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
            JdbcTestUtils.countRowsInTable(jdbcTemplate, "comparison_fund_values") == values.size()
    }

    def "it can retrieve fund values by last time and fund"() {
        given:
            List<FundValue> values = getFakeFundValues()
            fundValueRepository.saveAll(values)
        when:
            Optional<FundValue> epiLatestValue = fundValueRepository.findLastValueForFund(ComparisonFund.EPI)
            Optional<FundValue> marketLatestValue = fundValueRepository.findLastValueForFund(ComparisonFund.MARKET)
        then:
            epiLatestValue.isPresent()
            valuesEqual(epiLatestValue.get(), values[2])
            marketLatestValue.isPresent()
            valuesEqual(marketLatestValue.get(), values[0])
    }

    def "it handles missing fund values properly"() {
        when:
            Optional<FundValue> value = fundValueRepository.findLastValueForFund(ComparisonFund.EPI)
        then:
            !value.isPresent()
    }

    def "it can find the value closest for a time for the estonian average"() {
        given:
            List<FundValue> values = getFakeTimedFundValues()
            fundValueRepository.saveAll(values)
        when:
            FundValue value = fundValueRepository.getEstonianAverageFundValueProvider().getFundValueClosestToTime(parseInstant("1990-01-03"))
        then:
            valuesEqual(value, values[2])
    }

    private static List<FundValue> getFakeFundValues() {
        Instant now = Instant.now()
        Instant recent = Instant.ofEpochSecond(now.epochSecond - 100)
        return [
                new FundValue(now, 100, ComparisonFund.MARKET),
                new FundValue(recent, 10, ComparisonFund.MARKET),
                new FundValue(now, 200, ComparisonFund.EPI),
                new FundValue(recent, 20, ComparisonFund.EPI),
        ]
    }

    private static List<FundValue> getFakeTimedFundValues() {
        return [
                new FundValue(parseInstant("1990-01-04"), 100, ComparisonFund.EPI),
                new FundValue(parseInstant("1990-01-04"), 100, ComparisonFund.MARKET),
                new FundValue(parseInstant("1990-01-02"), 100, ComparisonFund.EPI),
                new FundValue(parseInstant("1990-01-02"), 100, ComparisonFund.MARKET),
                new FundValue(parseInstant("1990-01-01"), 100, ComparisonFund.EPI),
                new FundValue(parseInstant("1990-01-02"), 100, ComparisonFund.MARKET),
        ]
    }

    private static boolean valuesEqual(FundValue one, FundValue two) {
        return one.time == two.time && one.comparisonFund == two.comparisonFund && one.value == two.value
    }

    private static Instant parseInstant(String format) {
        return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant()
    }
}
