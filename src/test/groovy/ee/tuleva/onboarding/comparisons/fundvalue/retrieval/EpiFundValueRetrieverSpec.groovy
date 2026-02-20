package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.LocalDate

import static java.nio.charset.StandardCharsets.UTF_16
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(components = [EpiFundValueRetriever, PensionikeskusDataDownloader, EpiFundValueRetrieverConfiguration])
class EpiFundValueRetrieverSpec extends Specification {

  @Autowired
  EpiFundValueRetriever secondPillarEpiFundValueRetriever

  @Autowired
  EpiFundValueRetriever thirdPillarEpiFundValueRetriever

  @Autowired
  MockRestServiceServer server

  def "it is configured for the right index"() {
    expect:
    secondPillarEpiFundValueRetriever.key == EpiIndex.EPI.key
    thirdPillarEpiFundValueRetriever.key == EpiIndex.EPI_3.key
  }

  def "it successfully parses a valid epi fund value tsv for average epi values"() {
    given:
    def csv = """ignore\tthis\theader
2013-01-07\tEPI-10\t100,001
2013-01-07\tEPI-II\t100,23456
2013-01-08\tEPI-III\t101,001
2013-01-08\tEPI-10\t101,002
2013-01-08\tEPI-II\t101,23456
"""
    def startDate = LocalDate.parse("2013-01-07")
    def endDate = LocalDate.parse("2013-01-08")
    def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/?date_from=07.01.2013&date_to=08.01.2013&download=xls"
    server.expect(requestTo(expectedUrl))
        .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(UTF_16)), MediaType.TEXT_PLAIN))
    when:
    List<FundValue> values = secondPillarEpiFundValueRetriever.retrieveValuesForRange(startDate, endDate)
    then:
    values.size() == 2
    values[0].key() == EpiIndex.EPI.key
    values[0].value() == 100.23456
    values[1].key() == EpiIndex.EPI.key
    values[1].value() == 101.23456
  }

  def "when a row is misformed it is ignored"() {
    given:
    def csv = """ignore\tthis\theader
broken
2013-01-07\tEPI-II\t100,23456
2013-broken!\tEPI-III\t101,001
2013-01-08\tEPI-20
2013-01-08\tEPI\tyou-and-me-123
"""
    def startDate = LocalDate.parse("2013-01-07")
    def endDate = LocalDate.parse("2013-01-08")
    def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/?date_from=07.01.2013&date_to=08.01.2013&download=xls"
    server.expect(requestTo(expectedUrl))
        .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(UTF_16)), MediaType.TEXT_PLAIN))
    when:
    List<FundValue> values = secondPillarEpiFundValueRetriever.retrieveValuesForRange(startDate, endDate)
    then:
    values.size() == 1
    values[0].key() == EpiIndex.EPI.key
    values[0].value() == 100.23456
  }

  def "zero values are ignored"() {
    given:
    def csv = """ignore\tthis\theader
2013-01-07\tEPI-II\t0
"""
    def startDate = LocalDate.parse("2013-01-07")
    def endDate = LocalDate.parse("2013-01-07")
    def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/?date_from=07.01.2013&date_to=07.01.2013&download=xls"
    server.expect(requestTo(expectedUrl))
        .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(UTF_16)), MediaType.TEXT_PLAIN))
    when:
    List<FundValue> values = secondPillarEpiFundValueRetriever.retrieveValuesForRange(startDate, endDate)
    then:
    values.empty
  }

  def "when an invalid response is received, an empty list is returned"() {
    given:
    def csv = ""
    def startDate = LocalDate.parse("2013-01-07")
    def endDate = LocalDate.parse("2013-01-07")
    def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/?date_from=07.01.2013&date_to=07.01.2013&download=xls"
    server.expect(requestTo(expectedUrl))
        .andRespond(withSuccess(csv, MediaType.TEXT_PLAIN))
    when:
    List<FundValue> values = secondPillarEpiFundValueRetriever.retrieveValuesForRange(startDate, endDate)
    then:
    values.empty
  }
}
