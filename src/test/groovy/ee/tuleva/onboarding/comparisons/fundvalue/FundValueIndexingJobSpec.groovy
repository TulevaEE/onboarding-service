package ee.tuleva.onboarding.comparisons.fundvalue

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.BlackRockFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.DeutscheBoerseValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EODHDValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EuronextValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundNavRetrieverFactory
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.MorningstarNavRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever
import ee.tuleva.onboarding.deadline.PublicHolidays
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static java.util.Collections.singletonList

class FundValueIndexingJobSpec extends Specification {

    static final LocalDate TODAY = LocalDate.parse("2026-03-06")
    static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(), ZoneId.of("Europe/Tallinn"))

    FundValueRepository fundValueRepository = Mock(FundValueRepository)
    ComparisonIndexRetriever fundValueRetriever = Mock(ComparisonIndexRetriever)
    FundNavRetrieverFactory fundNavRetrieverFactory = Mock(FundNavRetrieverFactory)
    PublicHolidays publicHolidays = new PublicHolidays()

    FundValueIndexingJob fundValueIndexingJob = new FundValueIndexingJob(
        fundValueRepository,
        singletonList(fundValueRetriever),
        Mock(Environment),
        fundNavRetrieverFactory,
        CLOCK,
        publicHolidays)

    Logger jobLogger = (Logger) LoggerFactory.getLogger(FundValueIndexingJob)
    ListAppender<ILoggingEvent> logAppender = new ListAppender<>()

    def setup() {
        logAppender.start()
        jobLogger.addAppender(logAppender)
    }

    def cleanup() {
        jobLogger.detachAppender(logAppender)
    }

    private List<ILoggingEvent> errorEventsContaining(String substring) {
        return logAppender.list.findAll {
            it.level == Level.ERROR && it.formattedMessage.contains(substring)
        }
    }

    def "if no saved fund values found, downloads and saves from defined start time"() {
        given:
        List<FundValue> fundValues = fakeFundValues()
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.empty()
        fundValueRepository.save(_ as FundValue) >> { FundValue fv -> Optional.of(fv) }
        when:
        fundValueIndexingJob.runIndexingJob()
        then:
        1 * fundValueRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> fakeFundValues()
        1 * fundValueRepository.save(fundValues[0]) >> Optional.of(fundValues[0])
        1 * fundValueRepository.save(fundValues[1]) >> Optional.of(fundValues[1])
    }

    def "if saved fund values found, downloads from the next day after last fund value"() {
        given:
        List<FundValue> fundValues = fakeFundValues()
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRetriever.stalenessThreshold() >> Duration.ofDays(7)
        def lastFundValueTime = LocalDate.parse("2018-05-01")
        def dayFromLastFundValueTime = LocalDate.parse("2018-05-02")
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.of(aFundValue(UnionStockIndexRetriever.KEY, lastFundValueTime, 120.0))
        fundValueRepository.save(_ as FundValue) >> { FundValue fv -> Optional.of(fv) }
        when:
        fundValueIndexingJob.runIndexingJob()
        then:
        1 * fundValueRetriever.retrieveValuesForRange(dayFromLastFundValueTime, TODAY) >> fakeFundValues()
        1 * fundValueRepository.save(fundValues[0]) >> Optional.of(fundValues[0])
        1 * fundValueRepository.save(fundValues[1]) >> Optional.of(fundValues[1])
    }

    def "if last saved fund value was found today, does nothing"() {
        given:
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >> Optional.of(aFundValue(UnionStockIndexRetriever.KEY, TODAY, 120.0))
        when:
        fundValueIndexingJob.runIndexingJob()
        then:
        0 * fundValueRetriever.retrieveValuesForRange(_, _)
        0 * fundValueRepository.saveAll(_ as List<FundValue>)
    }

    def "logs ERROR when a daily index is past its 7-day staleness threshold"() {
        given:
        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRetriever.stalenessThreshold() >> Duration.ofDays(7)
        def staleDate = TODAY.minusDays(8)
        fundValueRepository.findLastValueForFund(UnionStockIndexRetriever.KEY) >>
            Optional.of(aFundValue(UnionStockIndexRetriever.KEY, staleDate, 100.0))
        fundValueRetriever.retrieveValuesForRange(_, _) >> []

        when:
        fundValueIndexingJob.runIndexingJob()

        then:
        def errors = errorEventsContaining("UNION_STOCK_INDEX")
        errors.size() == 1
        errors[0].formattedMessage.contains("lastDate=" + staleDate)
    }

    def "logs ERROR when CPI is past its 45-day staleness threshold"() {
        given:
        fundValueRetriever.getKey() >> "CPI_ECOICOP2"
        fundValueRetriever.stalenessThreshold() >> Duration.ofDays(45)
        def staleDate = TODAY.minusDays(50)
        fundValueRepository.findLastValueForFund("CPI_ECOICOP2") >>
            Optional.of(aFundValue("CPI_ECOICOP2", staleDate, 100.0))
        fundValueRetriever.retrieveValuesForRange(_, _) >> []

        when:
        fundValueIndexingJob.runIndexingJob()

        then:
        errorEventsContaining("CPI_ECOICOP2").size() == 1
    }

    def "does NOT log ERROR when last update is within the staleness threshold"() {
        given:
        fundValueRetriever.getKey() >> "CPI_ECOICOP2"
        fundValueRetriever.stalenessThreshold() >> Duration.ofDays(45)
        def freshDate = TODAY.minusDays(30)
        fundValueRepository.findLastValueForFund("CPI_ECOICOP2") >>
            Optional.of(aFundValue("CPI_ECOICOP2", freshDate, 100.0))
        fundValueRetriever.retrieveValuesForRange(_, _) >> []

        when:
        fundValueIndexingJob.runIndexingJob()

        then:
        errorEventsContaining("CPI_ECOICOP2").isEmpty()
    }

    def "logs ERROR when fetch returns 0 rows for a series past threshold"() {
        given:
        fundValueRetriever.getKey() >> "CPI_ECOICOP2"
        fundValueRetriever.stalenessThreshold() >> Duration.ofDays(45)
        def staleDate = TODAY.minusDays(50)
        fundValueRepository.findLastValueForFund("CPI_ECOICOP2") >>
            Optional.of(aFundValue("CPI_ECOICOP2", staleDate, 100.0))
        fundValueRetriever.retrieveValuesForRange(_, _) >> []

        when:
        fundValueIndexingJob.runIndexingJob()

        then:
        !errorEventsContaining("CPI_ECOICOP2").isEmpty()
    }

    def "refreshes all static retrievers"() {
        given:
        def retriever1 = Mock(ComparisonIndexRetriever)
        def retriever2 = Mock(ComparisonIndexRetriever)
        retriever1.getKey() >> "FUND_A"
        retriever2.getKey() >> "FUND_B"
        fundValueRepository.findLastValueForFund(_) >> Optional.empty()

        def job = new FundValueIndexingJob(
            fundValueRepository,
            [retriever1, retriever2],
            Mock(Environment),
            fundNavRetrieverFactory,
            CLOCK,
            publicHolidays)

        when:
        job.refreshAll()

        then:
        1 * retriever1.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * retriever2.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
    }

    def "continues refreshing other retrievers when one throws"() {
        given:
        def failingRetriever = Mock(ComparisonIndexRetriever)
        def successRetriever = Mock(ComparisonIndexRetriever)
        failingRetriever.getKey() >> "FAILING_FUND"
        successRetriever.getKey() >> "SUCCESS_FUND"
        fundValueRepository.findLastValueForFund(_) >> Optional.empty()

        def job = new FundValueIndexingJob(
            fundValueRepository,
            [failingRetriever, successRetriever],
            Mock(Environment),
            fundNavRetrieverFactory,
            CLOCK,
            publicHolidays)

        when:
        job.refreshAll()

        then:
        1 * failingRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> { throw new RuntimeException("FTP connection failed") }
        1 * successRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
    }

    def "after initDynamicRetrievers, refreshes both static and dynamic retrievers"() {
        given:
        def dynamicRetriever = Mock(ComparisonIndexRetriever)
        dynamicRetriever.getKey() >> "DYNAMIC_FUND"
        fundNavRetrieverFactory.createAll() >> [dynamicRetriever]

        fundValueRetriever.getKey() >> UnionStockIndexRetriever.KEY
        fundValueRepository.findLastValueForFund(_) >> Optional.empty()

        when:
        fundValueIndexingJob.initDynamicRetrievers()
        fundValueIndexingJob.refreshAll()

        then:
        1 * fundValueRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * dynamicRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
    }

    def "every FundTicker position is resolvable via a NAV-critical retriever before NAV publish"() {
        expect:
        FundTicker.values().each { ticker ->
            Set<String> sources = [] as Set<String>
            if (ticker.blackrockProductId != null) sources << BlackRockFundValueRetriever.KEY
            if (ticker.morningstarId != null) sources << MorningstarNavRetriever.KEY
            if (ticker.eodhdTicker != null) sources << EODHDValueRetriever.KEY
            if (ticker.xetraStorageKey.isPresent()) sources << DeutscheBoerseValueRetriever.KEY
            if (ticker.euronextParisStorageKey.isPresent()) sources << EuronextValueRetriever.KEY

            Set<String> covered = sources.intersect(FundValueIndexingJob.NAV_CRITICAL_RETRIEVER_KEYS)
            assert !covered.isEmpty():
                "FundTicker ${ticker.name()} (ISIN ${ticker.isin}) has no price source in NAV_CRITICAL_RETRIEVER_KEYS. " +
                "Sources=${sources}, critical=${FundValueIndexingJob.NAV_CRITICAL_RETRIEVER_KEYS}. " +
                "Either add a covering retriever key to NAV_CRITICAL_RETRIEVER_KEYS, " +
                "or ensure this FundTicker provides blackrockProductId / morningstarId / eodhdTicker."
        }
    }

    def "refreshForNavCalculation refreshes only NAV-critical retrievers"() {
        given:
        def blackrock = Mock(ComparisonIndexRetriever)
        def morningstar = Mock(ComparisonIndexRetriever)
        def eodhd = Mock(ComparisonIndexRetriever)
        def xetra = Mock(ComparisonIndexRetriever)
        def euronext = Mock(ComparisonIndexRetriever)
        def yahoo = Mock(ComparisonIndexRetriever)
        def cpi = Mock(ComparisonIndexRetriever)
        def dynamicEePensionFund = Mock(ComparisonIndexRetriever)

        blackrock.getKey() >> BlackRockFundValueRetriever.KEY
        morningstar.getKey() >> MorningstarNavRetriever.KEY
        eodhd.getKey() >> EODHDValueRetriever.KEY
        xetra.getKey() >> DeutscheBoerseValueRetriever.KEY
        euronext.getKey() >> EuronextValueRetriever.KEY
        yahoo.getKey() >> YahooFundValueRetriever.KEY
        cpi.getKey() >> "CPI"
        dynamicEePensionFund.getKey() >> "EE3600109435"

        fundValueRepository.findLastValueForFund(_) >> Optional.empty()
        fundNavRetrieverFactory.createAll() >> [dynamicEePensionFund]

        def job = new FundValueIndexingJob(
            fundValueRepository,
            [blackrock, morningstar, eodhd, xetra, euronext, yahoo, cpi],
            Mock(Environment),
            fundNavRetrieverFactory,
            CLOCK,
            publicHolidays)
        job.initDynamicRetrievers()

        when:
        job.refreshForNavCalculation()

        then:
        1 * blackrock.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * morningstar.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * eodhd.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * xetra.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * euronext.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        1 * yahoo.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, TODAY) >> []
        0 * cpi.retrieveValuesForRange(_, _)
        0 * dynamicEePensionFund.retrieveValuesForRange(_, _)
    }

    def "skips retrievers that require working day on weekends"() {
        given:
        def saturday = LocalDate.parse("2026-03-28") // Saturday
        def saturdayClock = Clock.fixed(saturday.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(), ZoneId.of("Europe/Tallinn"))

        def workingDayRetriever = Mock(ComparisonIndexRetriever)
        workingDayRetriever.getKey() >> "EXCHANGE_FUND"
        workingDayRetriever.requiresWorkingDay() >> true

        def anyDayRetriever = Mock(ComparisonIndexRetriever)
        anyDayRetriever.getKey() >> "CPI_INDEX"
        anyDayRetriever.requiresWorkingDay() >> false
        fundValueRepository.findLastValueForFund("CPI_INDEX") >> Optional.empty()

        def job = new FundValueIndexingJob(
            fundValueRepository,
            [workingDayRetriever, anyDayRetriever],
            Mock(Environment),
            fundNavRetrieverFactory,
            saturdayClock,
            publicHolidays)

        when:
        job.refreshAll()

        then:
        0 * workingDayRetriever.retrieveValuesForRange(_, _)
        1 * anyDayRetriever.retrieveValuesForRange(FundValueIndexingJob.EARLIEST_DATE, saturday) >> []
    }

    private static List<FundValue> fakeFundValues() {
        return [
            aFundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2017-01-01"), 100.0),
            aFundValue(UnionStockIndexRetriever.KEY, LocalDate.parse("2018-01-01"), 110.0),
        ]
    }
}
