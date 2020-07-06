package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import spock.lang.Specification
import java.time.LocalDate

class UnionStockIndexRetrieverSpec extends Specification {

    def key = UnionStockIndexRetriever.KEY
    def fundValueRepository = Mock(FundValueRepository)
    def retriever = new UnionStockIndexRetriever(fundValueRepository)

    void setup() {
    }

    def "retrieve value for date before 2020"() {
        given:
        def date = LocalDate.of(2019, 12, 30)
        def fundValue = new FundValue(key, date, 246.98)
        fundValueRepository.getGlobalStockValues() >> [fundValue]

        when:
        def result = retriever.retrieveValuesForRange(date, date)

        then:
        result == [fundValue]
    }

    def "retrieve value for date after 2020"() {
        given:
        def date = LocalDate.of(2020, 01, 02)
        def fundValue = new FundValue(key, date, UnionStockIndexRetriever.MULTIPLIER)
        fundValueRepository.getGlobalStockValues() >> [fundValue]

        when:
        def result = retriever.retrieveValuesForRange(date, date)

        then:
        result == [new FundValue(key, date, 1.0000000)]
    }


}
