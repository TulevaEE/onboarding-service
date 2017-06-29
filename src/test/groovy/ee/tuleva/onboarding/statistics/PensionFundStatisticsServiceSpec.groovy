package ee.tuleva.onboarding.statistics

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
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

	private static final String statisticsEndpointUrl = "endpointUrl"

	def setup() {
		service.statisticsEndpoint = statisticsEndpointUrl
	}

	def "GetPensionFundStatistics works with empty response"() {
		given:
		server.expect(requestTo(statisticsEndpointUrl))
				.andRespond(withSuccess(
				'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
						'<RESPONSE xmlns="http://corporate.epis.ee/producer/" ERROR_CODE="0"></RESPONSE>',
				MediaType.APPLICATION_XML))

		when:
		def response = service.getPensionFundStatistics()

		then:
		response.pensionFundStatistics == null
	}

	def "GetPensionFundStatistics works with one pension fund statistic"() {
		given:
		server.expect(requestTo(statisticsEndpointUrl))
				.andRespond(withSuccess(
				'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
						'<RESPONSE xmlns="http://corporate.epis.ee/producer/" ERROR_CODE="0">' +
							'<PENSION_FUND_STATISTICS ISIN="EE3600109435" VOLUME="27630899.99114" NAV="0.63337"/>' +
						'</RESPONSE>',
				MediaType.APPLICATION_XML))

		when:
		def statistics = service.getPensionFundStatistics()

		then:
		statistics.pensionFundStatistics != null
		statistics.pensionFundStatistics.size() == 1
		def fund = statistics.pensionFundStatistics.first()
		fund.isin == "EE3600109435"
		fund.nav == 0.63337
		fund.volume == 27_630_899.99114
	}

	def "GetPensionFundStatistics works with many fund statistics"() {
		given:
		def xmlResponse = readFile("/pensionFundStatistics.xml")

		server.expect(requestTo(statisticsEndpointUrl))
				.andRespond(withSuccess(xmlResponse, MediaType.APPLICATION_XML))

		when:
		def statistics = service.getPensionFundStatistics()

		then:
		statistics.pensionFundStatistics != null
		statistics.pensionFundStatistics.size() == 22
		def fund = statistics.pensionFundStatistics.first()
		fund.isin == "EE3600019717"
		fund.nav == 0.91511
		fund.volume == 59_899_459.39470
	}

	private String readFile(String fileName) {
		def resource = new ClassPathResource(fileName)
		new String(Files.readAllBytes(resource.getFile().toPath()))
	}

}
