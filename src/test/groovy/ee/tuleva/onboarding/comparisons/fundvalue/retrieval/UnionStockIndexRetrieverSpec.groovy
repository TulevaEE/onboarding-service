package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import spock.lang.Specification
import java.time.LocalDate

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
            new FundValue(key, LocalDate.of(2020, 03, 26), UnionStockIndexRetriever.MULTIPLIER),
            new FundValue(key, LocalDate.of(2020, 03, 27), UnionStockIndexRetriever.MULTIPLIER),
            new FundValue(key, LocalDate.of(2020, 03, 28), UnionStockIndexRetriever.MULTIPLIER),
            new FundValue(key, LocalDate.of(2020, 03, 30), UnionStockIndexRetriever.MULTIPLIER),
            new FundValue(key, LocalDate.of(2020, 03, 31), UnionStockIndexRetriever.MULTIPLIER)
        ]
        fundValueRepository.getGlobalStockValues() >> values

        when:
        List<FundValue> result = retriever.retrieveValuesForRange(LocalDate.of(2020, 03, 25), LocalDate.of(2020, 03, 31))

        then:
        List<FundValue> expectedValues = [
            new FundValue(key, LocalDate.of(2020, 03, 26), 1.0000000),
            new FundValue(key, LocalDate.of(2020, 03, 27), 1.0000000),
            new FundValue(key, LocalDate.of(2020, 03, 28), 1.0000000),
            new FundValue(key, LocalDate.of(2020, 03, 30), 1.0000000),
            new FundValue(key, LocalDate.of(2020, 03, 31), 1.0000000)
        ]
        result == expectedValues
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
