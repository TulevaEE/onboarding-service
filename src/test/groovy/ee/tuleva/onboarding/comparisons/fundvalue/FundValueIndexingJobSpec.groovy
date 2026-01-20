package ee.tuleva.onboarding.comparisons.fundvalue

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundNavRetrieverFactory
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import org.springframework.core.env.Environment
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static java.util.Collections.singletonList

class FundValueIndexingJobSpec extends Specification {

    FundValueRepository fundValueRepository = Mock(FundValueRepository)
    ComparisonIndexRetriever fundValueRetriever = Mock(ComparisonIndexRetriever)
    FundNavRetrieverFactory fundNavRetrieverFactory = Mock(FundNavRetrieverFactory)

    FundValueIndexingJob fundValueIndexingJob = new FundValueIndexingJob(
        fundValueRepository,
        singletonList(fundValueRetriever),
        Mock(Environment),
        fundNavRetrieverFactory)

    def "if no saved fund values found, downloads and saves from defined start time"() {
        List<FundValue> fundValues = fakeFundValues()
        given:
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.empty()
        fundValueRepository.save(_ as FundValue) >> { FundValue fv -> Optional.of(fv) }
        when:
        fundValueIndexingJob.runIndexingJob()
        then:
        1 * fundValueRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, LocalDate.now()) >> fakeFundValues()
        1 * fundValueRepository.save(fundValues[0]) >> Optional.of(fundValues[0])
        1 * fundValueRepository.save(fundValues[1]) >> Optional.of(fundValues[1])
    }

    def "if saved fund values found, downloads from the next day after last fund value"() {
        List<FundValue> fundValues = fakeFundValues()
        given:
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        def lastFundValueTime = LocalDate.parse("2018-05-01")
        def dayFromLastFundValueTime = LocalDate.parse("2018-05-02")
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.of(aFundValue(UnionStockIndexRetriever.KEY, lastFundValueTime, 120.0))
        fundValueRepository.save(_ as FundValue) >> { FundValue fv -> Optional.of(fv) }
        when:
        fundValueIndexingJob.runIndexingJob()
        then:
        1 * fundValueRetriever.retrieveValuesForRange(dayFromLastFundValueTime, LocalDate.now()) >> fakeFundValues()
        1 * fundValueRepository.save(fundValues[0]) >> Optional.of(fundValues[0])
        1 * fundValueRepository.save(fundValues[1]) >> Optional.of(fundValues[1])
    }

    def "if last saved fund value was found today, does nothing"() {
        given:
            fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
            def lastValueDate = LocalDate.now()
            fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.of(aFundValue(UnionStockIndexRetriever.KEY, lastValueDate, 120.0))
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            0 * fundValueRetriever.retrieveValuesForRange(_ as Instant, _ as Instant)
            0 * fundValueRepository.saveAll(_ as List<FundValue>)
    }

    private static List<FundValue> fakeFundValues() {
        return [
            aFundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2017-01-01"), 100.0),
            aFundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2018-01-01"), 110.0),
        ]
    }
}
