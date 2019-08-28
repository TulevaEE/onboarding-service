package ee.tuleva.onboarding.comparisons.fundvalue

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import org.springframework.core.env.Environment
import spock.lang.Specification

import java.text.SimpleDateFormat
import java.time.Instant

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
            1 * fundValueRetriever.retrieveValuesForRange(
                    FundValueIndexingJob.START_TIME,
                    { Instant time -> verifyTimeCloseToNow(time) } as Instant
            ) >> fakeFundValues()
            1 * fundValueRepository.saveAll(fakeFundValues())
    }

    def "if saved fund values found, downloads from the next day after last fund value"() {
        given:
            fundValueRetriever.getKey() >> WorldIndexValueRetriever.KEY
            Instant lastFundValueTime = parseInstant("2018-05-01")
            Instant dayFromlastFundValueTime = parseInstant("2018-05-02")
            fundValueRepository.findLastValueForFund(WorldIndexValueRetriever.KEY) >> Optional.of(new FundValue(lastFundValueTime, 120, WorldIndexValueRetriever.KEY))
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            1 * fundValueRetriever.retrieveValuesForRange(
                    dayFromlastFundValueTime,
                    { Instant time -> verifyTimeCloseToNow(time) } as Instant
            ) >> fakeFundValues()
            1 * fundValueRepository.saveAll(fakeFundValues())
    }

    def "if last saved fund value was found today, does nothing"() {
        given:
            fundValueRetriever.getKey() >> WorldIndexValueRetriever.KEY
            Instant lastFundValueTime = Instant.now()
            fundValueRepository.findLastValueForFund(WorldIndexValueRetriever.KEY) >> Optional.of(new FundValue(lastFundValueTime, 120, WorldIndexValueRetriever.KEY))
        when:
            fundValueIndexingJob.runIndexingJob()
        then:
            0 * fundValueRetriever.retrieveValuesForRange(_ as Instant, _ as Instant)
            0 * fundValueRepository.saveAll(_ as List<FundValue>)
    }

    private static List<FundValue> fakeFundValues() {
        return [
            new FundValue(parseInstant("2017-01-01"), 100.0, WorldIndexValueRetriever.KEY),
            new FundValue(parseInstant("2018-01-01"), 110.0, WorldIndexValueRetriever.KEY),
        ]
    }

    private static boolean verifyTimeCloseToNow(Instant time) {
        return time.epochSecond > (Instant.now().epochSecond - 100)
    }

    private static Instant parseInstant(String format) {
        return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant();
    }
}
