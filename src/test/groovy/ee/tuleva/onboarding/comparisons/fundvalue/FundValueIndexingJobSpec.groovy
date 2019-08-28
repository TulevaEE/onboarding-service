package ee.tuleva.onboarding.comparisons.fundvalue

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import org.springframework.core.env.Environment
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

class FundValueIndexingJobSpec extends Specification {

    FundValueRepository fundValueRepository
    ComparisonIndexRetriever fundValueRetriever

    FundValueIndexingJob fundValueIndexingJob

    void setup() {
        fundValueRepository = Mock(FundValueRepository)
        fundValueRetriever = Mock(ComparisonIndexRetriever)
        fundValueIndexingJob = new FundValueIndexingJob(
            fundValueRepository,
            Collections.singletonList(fundValueRetriever),
            Mock(Environment)
        )
    }

    def "if no saved fund values found, downloads and saves from defined start time"() {
        given:
            fundValueRetriever.getKey() >> WorldIndexValueRetriever.KEY
            fundValueRepository.findLastValueForFund(WorldIndexValueRetriever.KEY) >> Optional.empty()
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            1 * fundValueRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, LocalDate.now()) >> fakeFundValues()
            1 * fundValueRepository.saveAll(fakeFundValues())
    }

    def "if saved fund values found, downloads from the next day after last fund value"() {
        given:
            fundValueRetriever.getKey() >> WorldIndexValueRetriever.KEY
            def lastFundValueTime = LocalDate.parse("2018-05-01")
            def dayFromlastFundValueTime = LocalDate.parse("2018-05-02")
            fundValueRepository.findLastValueForFund(WorldIndexValueRetriever.KEY) >> Optional.of(new FundValue(WorldIndexValueRetriever.KEY, lastFundValueTime, 120.0))
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            1 * fundValueRetriever.retrieveValuesForRange(dayFromlastFundValueTime, LocalDate.now()) >> fakeFundValues()
            1 * fundValueRepository.saveAll(fakeFundValues())
    }

    def "if last saved fund value was found today, does nothing"() {
        given:
            fundValueRetriever.getKey() >> WorldIndexValueRetriever.KEY
            def lastValueDate = LocalDate.now()
            fundValueRepository.findLastValueForFund(WorldIndexValueRetriever.KEY) >> Optional.of(new FundValue(WorldIndexValueRetriever.KEY, lastValueDate, 120.0))
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            0 * fundValueRetriever.retrieveValuesForRange(_ as Instant, _ as Instant)
            0 * fundValueRepository.saveAll(_ as List<FundValue>)
    }

    private static List<FundValue> fakeFundValues() {
        return [
            new FundValue(WorldIndexValueRetriever.KEY, LocalDate.parse("2017-01-01"), 100.0),
            new FundValue(WorldIndexValueRetriever.KEY, LocalDate.parse("2018-01-01"), 110.0),
        ]
    }
}
