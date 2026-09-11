package ee.tuleva.onboarding.comparisons.fundvalue

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.BlackRockFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.DeutscheBoerseValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EODHDValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EuronextValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.MorningstarNavRetriever
import ee.tuleva.onboarding.instrument.InstrumentReferenceService
import ee.tuleva.onboarding.time.ClockConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import spock.lang.Specification

@DataJpaTest
@ComponentScan(basePackageClasses = InstrumentReferenceService)
@Import(ClockConfig)
class NavCriticalPriceSourceSpec extends Specification {

    @Autowired
    InstrumentReferenceService instrumentReferenceService

    def "every active instrument is resolvable via a NAV-critical retriever before NAV publish"() {
        given:
        def instruments = instrumentReferenceService.activeInstruments()

        expect:
        !instruments.isEmpty()

        and:
        instruments.each { instrument ->
            Set<String> sources = [] as Set<String>
            if (instrument.blackrockProductId != null) sources << BlackRockFundValueRetriever.KEY
            if (instrument.morningstarId != null) sources << MorningstarNavRetriever.KEY
            if (instrument.eodhdTicker != null) sources << EODHDValueRetriever.KEY
            if (instrument.xetraStorageKey.isPresent()) sources << DeutscheBoerseValueRetriever.KEY
            if (instrument.euronextParisStorageKey.isPresent()) sources << EuronextValueRetriever.KEY

            Set<String> covered = sources.intersect(FundValueIndexingJob.NAV_CRITICAL_RETRIEVER_KEYS)
            assert !covered.isEmpty():
                "instrument_reference row ${instrument.isin} has no price source in NAV_CRITICAL_RETRIEVER_KEYS. " +
                "Sources=${sources}, critical=${FundValueIndexingJob.NAV_CRITICAL_RETRIEVER_KEYS}. " +
                "Either add a covering retriever key to NAV_CRITICAL_RETRIEVER_KEYS, " +
                "or give the row a blackrock_product_id / morningstar_id / eodhd_ticker."
        }
    }
}
