package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RequestCallback
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.LocalDate

class EpiFundValueRetrieverSpec extends Specification {

    RestTemplate restTemplate
    EPIFundValueRetriever epiFundValueRetriever

    void setup() {
        restTemplate = Mock(RestTemplate)
        epiFundValueRetriever = new EPIFundValueRetriever(restTemplate)
    }

    def "it is configured for the right fund"() {
        when:
            def retrievalFund = epiFundValueRetriever.getKey()
        then:
            retrievalFund == EPIFundValueRetriever.KEY
    }

    def "it successfully parses a valid epi fund value tsv for average epi values"() {
        given:
            ClientHttpResponse response = createResponse(HttpStatus.OK, """ignore\tthis\theader
2013-01-07\tEPI-25\t100,001
2013-01-07\tEPI\t100,23456
2013-01-08\tEPI-50\t101,001
2013-01-08\tEPI-25\t101,002
2013-01-08\tEPI\t101,23456
""")
            List<FundValue> expectedValues = [
                    new FundValue(EPIFundValueRetriever.KEY, LocalDate.parse("2013-01-07"), 100.23456),
                    new FundValue(EPIFundValueRetriever.KEY, LocalDate.parse("2013-01-08"), 101.23456),
            ]
        when:
            List<FundValue> values = epiFundValueRetriever.retrieveValuesForRange(LocalDate.now(), LocalDate.now())
        then:
            1 * restTemplate.execute(_, _, _, _, _) >> {
                String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                    handler.extractData(response)
            }
            values == expectedValues
    }

    def "it calls pensionikeskus with the correct dates"() {
        given:
        def startTime = LocalDate.parse("2018-02-03")
        def endTime = LocalDate.parse("2018-04-05")
        when:
        epiFundValueRetriever.retrieveValuesForRange(startTime, endTime)
        then:
        1 * restTemplate.execute("https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/?date_from=03.02.2018&date_to=05.04.2018&download=xls", _, _, _, _)
    }

    def "when a row is misformed it is ignored"() {
        given:
            ClientHttpResponse response = createResponse(HttpStatus.OK, """ignore\tthis\theader
broken
2013-01-07\tEPI\t100,23456
2013-broken!\tEPI-50\t101,001
2013-01-08\tEPI-20
2013-01-08\tEPI\tyou-and-me-123
""")
            List<FundValue> expectedValues = [
                    new FundValue(EPIFundValueRetriever.KEY, LocalDate.parse("2013-01-07"), 100.23456),
            ]
        when:
            List<FundValue> values = epiFundValueRetriever.retrieveValuesForRange(LocalDate.now(), LocalDate.now())
        then:
            1 * restTemplate.execute(_, _, _, _, _) >> {
                String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                    handler.extractData(response)
            }
            values == expectedValues
    }

    def "zero values are ignored"() {
        given:
        ClientHttpResponse response = createResponse(HttpStatus.OK, """ignore\tthis\theader
2013-01-07\tEPI\t0
""")
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        when:
        List<FundValue> values = epiFundValueRetriever.retrieveValuesForRange(LocalDate.now(), LocalDate.now())
        then:
        values.empty
    }

    def "when an invalid response is received, an empty list is returned"() {
        given:
        ClientHttpResponse response = createResponse(HttpStatus.BAD_REQUEST, "")
        when:
        List<FundValue> values = epiFundValueRetriever.retrieveValuesForRange(LocalDate.now(), LocalDate.now())
        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        values.empty
    }

    private ClientHttpResponse createResponse(HttpStatus status, String csvBody) {
        ClientHttpResponse response = Mock(ClientHttpResponse)
        response.getStatusCode() >> status
        response.getBody() >> new ByteArrayInputStream(csvBody.getBytes(StandardCharsets.UTF_16))
        return response;
    }
}
