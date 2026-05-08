package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.fund.NavDto
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.error.response.ErrorsResponse
import spock.lang.Specification

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundNavRetriever.PROVIDER
import static java.time.LocalDate.parse
import static org.assertj.core.api.Assertions.assertThat

class FundNavRetrieverSpec extends Specification {

    def isin = "EE1111"
    def episService = Mock(EpisService)
    def retriever = new FundNavRetriever(episService, isin)

    def "key is isin"() {
        expect:
        retriever.key == isin
    }

    def "retrieve values for range"() {
        given:
        def startDate = parse("2019-08-19")
        def endDate = parse("2019-08-22")
        episService.getNav(isin, parse("2019-08-19")) >> new NavDto(isin, parse("2019-08-19"), 19.0)
        episService.getNav(isin, parse("2019-08-20")) >> { throw new ErrorsResponseException(new ErrorsResponse()) }
        episService.getNav(isin, parse("2019-08-21")) >> new NavDto(isin, parse("2019-08-21"), 21.0)
        episService.getNav(isin, parse("2019-08-22")) >> new NavDto(isin, parse("2019-08-22"), 22.0)
        when:
        def result = retriever.retrieveValuesForRange(startDate, endDate)

        then:
        def expected = [
            aFundValue(isin, parse("2019-08-19"), 19.0, PROVIDER),
            aFundValue(isin, parse("2019-08-21"), 21.0, PROVIDER),
            aFundValue(isin, parse("2019-08-22"), 22.0, PROVIDER)
        ]
        assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }
}
