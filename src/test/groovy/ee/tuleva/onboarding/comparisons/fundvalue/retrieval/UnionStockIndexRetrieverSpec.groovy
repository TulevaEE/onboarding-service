package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever.PROVIDER
import static org.assertj.core.api.Assertions.assertThat

class UnionStockIndexRetrieverSpec extends Specification {

    def key = UnionStockIndexRetriever.KEY
    FundValueRepository fundValueRepository
    UnionStockIndexRetriever retriever

    void setup() {
        fundValueRepository = Mock(FundValueRepository)
        retriever = new UnionStockIndexRetriever(fundValueRepository)
    }

    def "it is configured for the right fund"() {
        when:
        def retrievalFund = retriever.getKey()

        then:
        retrievalFund == UnionStockIndexRetriever.KEY
    }

    def "it should work with missing index values"() {
        given:
        List<FundValue> values = [
            aFundValue(key, LocalDate.of(2020, 03, 26), UnionStockIndexRetriever.MULTIPLIER),
            aFundValue(key, LocalDate.of(2020, 03, 27), UnionStockIndexRetriever.MULTIPLIER),
            aFundValue(key, LocalDate.of(2020, 03, 28), UnionStockIndexRetriever.MULTIPLIER),
            aFundValue(key, LocalDate.of(2020, 03, 30), UnionStockIndexRetriever.MULTIPLIER),
            aFundValue(key, LocalDate.of(2020, 03, 31), UnionStockIndexRetriever.MULTIPLIER)
        ]
        fundValueRepository.getGlobalStockValues() >> values

        when:
        List<FundValue> result = retriever.retrieveValuesForRange(LocalDate.of(2020, 03, 25), LocalDate.of(2020, 03, 31))

        then:
        def expected = [
            aFundValue(key, LocalDate.of(2020, 03, 26), 1.0, PROVIDER),
            aFundValue(key, LocalDate.of(2020, 03, 27), 1.0, PROVIDER),
            aFundValue(key, LocalDate.of(2020, 03, 28), 1.0, PROVIDER),
            aFundValue(key, LocalDate.of(2020, 03, 30), 1.0, PROVIDER),
            aFundValue(key, LocalDate.of(2020, 03, 31), 1.0, PROVIDER)
        ]
        assertThat(result).usingRecursiveComparison()
            .withEqualsForType({ a, b -> a.compareTo(b) == 0 }, BigDecimal)
            .ignoringFields("updatedAt")
            .isEqualTo(expected)
    }

    def "retrieve value for date before 2020"() {
        given:
        def date = LocalDate.of(2019, 12, 30)
        def fundValue = aFundValue(key, date, 246.98)
        fundValueRepository.getGlobalStockValues() >> [fundValue]

        when:
        def result = retriever.retrieveValuesForRange(date, date)

        then:
        def expected = [aFundValue(key, date, 246.98, PROVIDER)]
        assertThat(result).usingRecursiveComparison()
            .withEqualsForType({ a, b -> a.compareTo(b) == 0 }, BigDecimal)
            .ignoringFields("updatedAt")
            .isEqualTo(expected)
    }

    def "retrieve value for date after 2020"() {
        given:
        def date = LocalDate.of(2020, 01, 02)
        def fundValue = aFundValue(key, date, UnionStockIndexRetriever.MULTIPLIER)
        fundValueRepository.getGlobalStockValues() >> [fundValue]

        when:
        def result = retriever.retrieveValuesForRange(date, date)

        then:
        def expected = [aFundValue(key, date, 1.0, PROVIDER)]
        assertThat(result).usingRecursiveComparison()
            .withEqualsForType({ a, b -> a.compareTo(b) == 0 }, BigDecimal)
            .ignoringFields("updatedAt")
            .isEqualTo(expected)
    }
}
