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

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever.PROVIDER
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.LocalDate.parse
import static org.assertj.core.api.Assertions.assertThat

class WorldIndexValueRetrieverSpec extends Specification {

    RestTemplate restTemplate
    WorldIndexValueRetriever retriever

    void setup() {
        restTemplate = Mock(RestTemplate)
        retriever = new WorldIndexValueRetriever(restTemplate)
    }

    def "it is configured for the right fund"() {
        when:
        def retrievalFund = retriever.getKey()

        then:
        retrievalFund == WorldIndexValueRetriever.KEY
    }

    def "it successfully parses a valid fund values"() {
        given:
        String responseBody = """"Indeks Components ","ISIN","Share","Last price nav","date","expense ratio","",""
"","","70%","24.1800","","0.25%","",""
"","","30%","223.8900","","0.20%","",""
"1-Jul-2018","24.18","223.89","8.1931","0.3617","279.09","",""
"17-July-2018","24.05","224.01","8.1931","0.3617","278.07","",""
"""
        ClientHttpResponse response = createResponse(HttpStatus.OK, responseBody)

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2018-01-01"), parse("2019-01-01"))

        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        def expected = [
            aFundValue(WorldIndexValueRetriever.KEY, parse("2018-07-01"), 279.09, PROVIDER),
            aFundValue(WorldIndexValueRetriever.KEY, parse("2018-07-17"), 278.07, PROVIDER)
        ]
        assertThat(values).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }

    def "it filters out lines with incorrect dates"() {
        given:
        def startTime = parse("2018-07-16")
        def endTime = parse("2018-07-17")
        String responseBody = """"Indeks Components ","ISIN","Share","Last price nav","date","expense ratio","","","","","","","","","","","","","","","","","","","",""
"","","70%","24.1800","","0.25%","","","","","","","","","","","","","","","","","","","",""
"","","30%","223.8900","","0.20%","","","","","","","","","","","","","","","","","","","",""
"18-Jul-2018","24.18","223.89","6.7372","0.2974","229.49","","","","","","","","","","","","","","","","","","","",""
"17-Jul-2018","24.05","224.01","6.7372","0.2974","228.65","","","","","","","","","","","","","","","","","","","",""
"16-Jul-2018","23.94","223.43","6.7372","0.2974","227.74","","","","","","","","","","","","","","","","","","","",""
"13-Jul-2018","24.04","223.77","6.7372","0.2974","228.52","","","","","","","","","","","","","","","","","","","",""
"""
        ClientHttpResponse response = createResponse(HttpStatus.OK, responseBody)

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(startTime, endTime)

        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        def expected = [
            aFundValue(WorldIndexValueRetriever.KEY, parse("2018-07-17"), 228.65, PROVIDER),
            aFundValue(WorldIndexValueRetriever.KEY, parse("2018-07-16"), 227.74, PROVIDER)
        ]
        assertThat(values).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }

    def "when a row is misformed it is ignored"() {
        given:
        ClientHttpResponse response = createResponse(HttpStatus.OK, """i"Indeks Components ","ISIN","Share","Last price nav","date","expense ratio","",""
broken
"","","70%","24.1800","","0.25%","",""
"","","30%","223.8900","","0.20%","",""
"18-Jul-2018","24.18","223.89","8.1931","0.3617","279.09","",""
"17-Jul-2018","24.05","224.01","8.1931","0.3617","278.07","",""
""")

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parse("2018-01-01"), parse("2019-01-01"))

        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        def expected = [
            aFundValue(WorldIndexValueRetriever.KEY, parse("2018-07-18"), 279.09, PROVIDER),
            aFundValue(WorldIndexValueRetriever.KEY, parse("2018-07-17"), 278.07, PROVIDER)
        ]
        assertThat(values).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }

    def "when an invalid response is received, an empty list is returned"() {
        given:
        ClientHttpResponse response = createResponse(HttpStatus.BAD_REQUEST, "")

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(LocalDate.now(), LocalDate.now())

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
        response.getBody() >> new ByteArrayInputStream(csvBody.getBytes(UTF_8))
        return response
    }
}
