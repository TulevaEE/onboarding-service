package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.ComparisonFund
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RequestCallback
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant

class WorldIndexValueRetrieverSpec extends Specification {

    RestTemplate restTemplate
    WorldIndexValueRetriever retriever

    void setup() {
        restTemplate = Mock(RestTemplate)
        retriever = new WorldIndexValueRetriever(restTemplate)
    }

    def "it is configured for the right fund"() {
        when:
        ComparisonFund retrievalFund = retriever.getRetrievalFund()

        then:
        retrievalFund == ComparisonFund.MARKET
    }

    def "it successfully parses a valid fund values"() {
        given:
        String responseBody = """"Indeks Components ","ISIN","Share","Last price nav","date","expense ratio","",""
"","","70%","24.1800","","0.25%","",""
"","","30%","223.8900","","0.20%","",""
"18-Jul-2018","24.18","223.89","8.1931","0.3617","279.09","",""
"17-Jul-2018","24.05","224.01","8.1931","0.3617","278.07","",""
"""
        ClientHttpResponse response = createResponse(HttpStatus.OK, responseBody)
        List<FundValue> expectedValues = [
                FundValue.builder().comparisonFund(ComparisonFund.MARKET).value(279.09).time(parseInstant("2018-07-18")).build(),
                FundValue.builder().comparisonFund(ComparisonFund.MARKET).value(278.07).time(parseInstant("2018-07-17")).build(),
        ]

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parseInstant("2018-01-01"), parseInstant("2019-01-01"))

        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        values == expectedValues
    }

    def "it filters out lines with incorrect dates"() {
        given:
        Instant startTime = parseInstant("2018-07-16")
        Instant endTime = parseInstant("2018-07-17")
        String responseBody = """"Indeks Components ","ISIN","Share","Last price nav","date","expense ratio","","","","","","","","","","","","","","","","","","","",""
"","","70%","24.1800","","0.25%","","","","","","","","","","","","","","","","","","","",""
"","","30%","223.8900","","0.20%","","","","","","","","","","","","","","","","","","","",""
"18-Jul-2018","24.18","223.89","6.7372","0.2974","229.49","","","","","","","","","","","","","","","","","","","",""
"17-Jul-2018","24.05","224.01","6.7372","0.2974","228.65","","","","","","","","","","","","","","","","","","","",""
"16-Jul-2018","23.94","223.43","6.7372","0.2974","227.74","","","","","","","","","","","","","","","","","","","",""
"13-Jul-2018","24.04","223.77","6.7372","0.2974","228.52","","","","","","","","","","","","","","","","","","","",""
"""
        ClientHttpResponse response = createResponse(HttpStatus.OK, responseBody)
        List<FundValue> expectedValues = [
                FundValue.builder().comparisonFund(ComparisonFund.MARKET).value(228.65).time(parseInstant("2018-07-17")).build(),
                FundValue.builder().comparisonFund(ComparisonFund.MARKET).value(227.74).time(parseInstant("2018-07-16")).build(),
        ]

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(startTime, endTime)

        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        values == expectedValues

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
        List<FundValue> expectedValues = [
            FundValue.builder().comparisonFund(ComparisonFund.MARKET).value(279.09).time(parseInstant("2018-07-18")).build(),
            FundValue.builder().comparisonFund(ComparisonFund.MARKET).value(278.07).time(parseInstant("2018-07-17")).build(),
        ]

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(parseInstant("2018-01-01"), parseInstant("2019-01-01"))

        then:
        1 * restTemplate.execute(_, _, _, _, _) >> {
            String url, HttpMethod method, RequestCallback callback, ResponseExtractor<List<FundValue>> handler, Object[] uriVariables ->
                handler.extractData(response)
        }
        values == expectedValues
    }

    def "when an invalid response is received, an empty list is returned"() {
        given:
        ClientHttpResponse response = createResponse(HttpStatus.BAD_REQUEST, "")

        when:
        List<FundValue> values = retriever.retrieveValuesForRange(Instant.now(), Instant.now())

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
        response.getBody() >> new ByteArrayInputStream(csvBody.getBytes(StandardCharsets.UTF_8))
        return response;
    }

    private static Instant parseInstant(String format) {
        return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant()
    }
}
