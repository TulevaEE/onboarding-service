package ee.tuleva.onboarding.comparisons.fundvalue.persistence

import ee.tuleva.onboarding.OnboardingServiceApplication
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
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
            Optional<FundValue> marketLatestValue = fundValueRepository.findLastValueForFund(WorldIndexValueRetriever.KEY)
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
            new FundValue(EPIFundValueRetriever.KEY, parse("1990-01-04"), 104.0),
            new FundValue(EPIFundValueRetriever.KEY, parse("1990-01-02"), 102.0),
            new FundValue(EPIFundValueRetriever.KEY, parse("1990-01-01"), 101.0),
            new FundValue(WorldIndexValueRetriever.KEY, parse("1990-01-04"), 204.0),
            new FundValue(WorldIndexValueRetriever.KEY, parse("1990-01-02"), 202.0),
            new FundValue(WorldIndexValueRetriever.KEY, parse("1990-01-01"), 201.0),
        ]
        fundValueRepository.saveAll(values)
        when:
        Optional<FundValue> epiValue = fundValueRepository.getLatestValue(EPIFundValueRetriever.KEY, parse("1990-01-03"))
        Optional<FundValue> marketValue = fundValueRepository.getLatestValue(WorldIndexValueRetriever.KEY, parse("1990-01-06"))
        Optional<FundValue> olderValue = fundValueRepository.getLatestValue(WorldIndexValueRetriever.KEY, parse("1970-01-01"))
        then:
        epiValue.isPresent()
        marketValue.isPresent()
        olderValue.isEmpty()
        epiValue.get().getValue() == 102.0
        marketValue.get().getValue() == 204.0
    }

    private static List<FundValue> getFakeFundValues() {
        def today = LocalDate.now()
        def yesterday = LocalDate.now().minusDays(1)
        return [
            new FundValue(WorldIndexValueRetriever.KEY, today, 100.0),
            new FundValue(WorldIndexValueRetriever.KEY, yesterday, 10.0),
            new FundValue(EPIFundValueRetriever.KEY, today, 200.0),
            new FundValue(EPIFundValueRetriever.KEY, yesterday, 20.0),
        ]
    }

    private static boolean valuesEqual(FundValue one, FundValue two) {
        return one.date == two.date && one.comparisonFund == two.comparisonFund && one.value == two.value
    }
}
