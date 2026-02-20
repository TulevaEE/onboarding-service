package ee.tuleva.onboarding.fund.statistics

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.nio.file.Files

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(PensionFundStatisticsService)
class PensionFundStatisticsServiceSpec extends Specification {

    @Autowired
    private PensionFundStatisticsService service

    @Autowired
    private MockRestServiceServer server

    private static final String secondPillarEndpoint = "/2ndPillarEndpoint"
    private static final String thirdPillarEndpoint = "/3rdPillarEndpoint"


    def setup() {
        service.secondPillarEndpoint = secondPillarEndpoint
        service.thirdPillarEndpoint = thirdPillarEndpoint
    }

    def "getPensionFundStatistics works with empty response"() {
        given:
        server.expect(requestTo(secondPillarEndpoint))
            .andRespond(withSuccess(
                '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
                    '<RESPONSE xmlns="http://corporate.epis.ee/producer/" ERROR_CODE="0"></RESPONSE>',
                MediaType.APPLICATION_XML))

        when:
        def statistics = service.getPensionFundStatistics(secondPillarEndpoint)

        then:
        statistics == []
    }

    def "getPensionFundStatistics works with one pension fund statistic"() {
        given:
        server.expect(requestTo(secondPillarEndpoint))
            .andRespond(withSuccess(
                '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
                    '<RESPONSE xmlns="http://corporate.epis.ee/producer/" ERROR_CODE="0">' +
                    '<PENSION_FUND_STATISTICS ' +
                    'ISIN="EE3600109435" VOLUME="27630899.99114" NAV="0.63337" ACTIVE_COUNT="3315"' +
                    '/>' +
                    '</RESPONSE>',
                MediaType.APPLICATION_XML))

        when:
        def statistics = service.getPensionFundStatistics(secondPillarEndpoint)

        then:
        statistics != null
        statistics.size() == 1
        with(statistics.first()) {
            isin == "EE3600109435"
            nav == 0.63337
            volume == 27_630_899.99114
            activeCount == 3315
        }
    }

    def "getPensionFundStatistics works with many fund statistics"() {
        given:
        def xmlResponse = readFile("/pensionFundStatistics.xml")

        server.expect(requestTo(secondPillarEndpoint))
            .andRespond(withSuccess(xmlResponse, MediaType.APPLICATION_XML))

        when:
        def statistics = service.getPensionFundStatistics(secondPillarEndpoint)

        then:
        statistics != null
        statistics.size() == 22
        with(statistics.first()) {
            isin == "EE3600019717"
            nav == 0.91511
            volume == 59_899_459.39470
            activeCount == 12_614
        }
    }

    def "can get statistics for both 2nd and 3rd pillar"() {
        given:
        def secondPillarXmlResponse = readFile("/pensionFundStatistics.xml")
        def thirdPillarXmlResponse = readFile("/pensionFundStatistics_3.xml")

        server.expect(requestTo(secondPillarEndpoint))
            .andRespond(withSuccess(secondPillarXmlResponse, MediaType.APPLICATION_XML))

        server.expect(requestTo(thirdPillarEndpoint))
            .andRespond(withSuccess(thirdPillarXmlResponse, MediaType.APPLICATION_XML))

        when:
        def statistics = service.getPensionFundStatistics()

        then:
        statistics != null
        statistics.size() == 33
        with(statistics.first()) {
            isin == "EE3600019717"
            nav == 0.91511
            volume == 59_899_459.39470
            activeCount == 12_614
        }
        with(statistics.last()) {
            isin == "EE3600109484"
            nav == 1.0956
            volume == 187_496.89850
            activeCount == 0
        }
    }

    private String readFile(String fileName) {
        def resource = new ClassPathResource(fileName)
        new String(Files.readAllBytes(resource.getFile().toPath()))
    }

}
