package ee.tuleva.onboarding.comparisons.fundvalue

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundNavRetrieverFactory
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import org.springframework.core.env.Environment
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

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
            fundValueRepository.findExistingValueForFund(_ as FundValue) >> Optional.empty()
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            1 * fundValueRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, LocalDate.now()) >> fakeFundValues()
            1 * fundValueRepository.save(fundValues[0])
            1 * fundValueRepository.save(fundValues[1])
    }

    def "if saved fund values exist in downloaded funds, update existing one"() {
        List<FundValue> fundValues = fakeFundValues()
        given:
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.empty()
        fundValueRepository.findExistingValueForFund(_ as FundValue) >> {value -> Optional.of(value)}
        when:
        fundValueIndexingJob.runIndexingJob()
        then:
        1 * fundValueRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, LocalDate.now()) >> fakeFundValues()
        1 * fundValueRepository.update(fundValues[0])
        1 * fundValueRepository.update(fundValues[1])
    }

    def "if saved fund values found, downloads from the next day after last fund value"() {
        List<FundValue> fundValues = fakeFundValues()
        given:
            fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
            def lastFundValueTime = LocalDate.parse("2018-05-01")
            def dayFromLastFundValueTime = LocalDate.parse("2018-05-02")
            fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.of(new FundValue(UnionStockIndexRetriever.KEY, lastFundValueTime, 120.0))
            fundValueRepository.findExistingValueForFund(_ as FundValue) >> Optional.empty()
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            1 * fundValueRetriever.retrieveValuesForRange(dayFromLastFundValueTime, LocalDate.now()) >> fakeFundValues()
            1 * fundValueRepository.save(fundValues[0])
            1 * fundValueRepository.save(fundValues[1])
    }

    def "if last saved fund value was found today, does nothing"() {
        given:
            fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
            def lastValueDate = LocalDate.now()
            fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.of(new FundValue(UnionStockIndexRetriever.KEY, lastValueDate, 120.0))
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            0 * fundValueRetriever.retrieveValuesForRange(_ as Instant, _ as Instant)
            0 * fundValueRepository.saveAll(_ as List<FundValue>)
    }

    private static List<FundValue> fakeFundValues() {
        return [
            new FundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2017-01-01"), 100.0),
            new FundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2018-01-01"), 110.0),
        ]
    }
}
