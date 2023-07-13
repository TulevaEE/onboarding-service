package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.fund.NavDto
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Specification

import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.LocalDate.parse

class FundNavRetrieverSpec extends Specification {

    def isin = "EE1111"
    def episService = Mock(EpisService)
    def retriever = new FundNavRetriever(episService, isin)

    def "key is isin"() {
        given:
        when:
        _
        then:
        retriever.key == isin
    }

    def "retrieve values for range"() {
        given:
        def startDate = parse("2019-08-19")
        def endDate = parse("2019-08-22")
        episService.getNav(isin, parse("2019-08-19")) >> new NavDto(isin, parse("2019-08-19"), 19.0)
        episService.getNav(isin, parse("2019-08-20")) >> {
          throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "message", null, new byte[0], UTF_8)
        }
        episService.getNav(isin, parse("2019-08-21")) >> new NavDto(isin, parse("2019-08-21"), 21.0)
        episService.getNav(isin, parse("2019-08-22")) >> new NavDto(isin, parse("2019-08-22"), 22.0)
        when:
        def result = retriever.retrieveValuesForRange(startDate, endDate)
        then:
        result == [
            new FundValue(isin, parse("2019-08-19"), 19.0),
            new FundValue(isin, parse("2019-08-21"), 21.0),
            new FundValue(isin, parse("2019-08-22"), 22.0)
        ]
    }
}
