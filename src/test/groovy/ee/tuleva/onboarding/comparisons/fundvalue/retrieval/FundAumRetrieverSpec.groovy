package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.LocalDate

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(components = [FundAumRetriever, PensionikeskusDataDownloader])
class FundAumRetrieverSpec extends Specification {

  @Autowired
  FundAumRetriever fundAumRetriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
  }

  def "should retrieve AUM fund values from CSV"() {
    given:
    def csv = """Date\tFund\tShortname\tISIN\tNet assets\tChange %
2024-11-05\tLuminor 16-50 pension fund\tNPK75\tEE3600103503\t208800943\t0
2024-11-05\tSwedbank Pension Fund Generation 1980-89\tSWK75\tEE3600103248\t516440444\t0
2024-11-05\tLHV Pensionifond XL\tLXK75\tEE3600019766\t266881342\t0
"""
    def startDate = LocalDate.parse("2024-11-05")
    def endDate = LocalDate.parse("2024-11-05")
    def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/value-of-assets-of-funded-pension/?date_from=05.11.2024&date_to=05.11.2024&download=xls"
    server.expect(requestTo(expectedUrl))
        .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_16)), MediaType.TEXT_PLAIN))

    when:
    List<FundValue> result = fundAumRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    result.size() == 3
    result[0].key() == "AUM_EE3600103503"
    result[0].value() == 208800943
    result[1].key() == "AUM_EE3600103248"
    result[1].value() == 516440444
    result[2].key() == "AUM_EE3600019766"
    result[2].value() == 266881342
  }
}
