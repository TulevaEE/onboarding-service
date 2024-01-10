package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(CpiValueRetriever)
class CpiValueRetrieverSpec extends Specification {

  @Autowired
  CpiValueRetriever cpiValueRetriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
  }

  def "it is configured for the right fund"() {
    when:
    def retrievalFund = cpiValueRetriever.getKey()
    then:
    retrievalFund == CpiValueRetriever.KEY
  }

  def "it successfully parses a valid CPI TSV file"() {
    given:
    def mockApiResponse =
        """freq,unit,coicop,geo\\TIME_PERIOD\t1996-01\t1996-02\t1996-03
M,I96,CP00,EE\t93.8\t96.8\t98.4"""

    server.expect(requestTo("https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/prc_hicp_midx/?format=TSV&compressed=true"))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    LocalDate startDate = LocalDate.of(1996, 1, 1)
    LocalDate endDate = LocalDate.of(1996, 3, 1)
    def result = cpiValueRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    result == [
        new FundValue(CpiValueRetriever.KEY, LocalDate.of(1996, 1, 1), 93.8),
        new FundValue(CpiValueRetriever.KEY, LocalDate.of(1996, 2, 1), 96.8),
        new FundValue(CpiValueRetriever.KEY, LocalDate.of(1996, 3, 1), 98.4)
    ]
  }

  private byte[] gzip(String input) {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
      gzipOutputStream.write(input.getBytes(StandardCharsets.UTF_8));
      gzipOutputStream.close();
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Error compressing string", e);
    }
  }
}

