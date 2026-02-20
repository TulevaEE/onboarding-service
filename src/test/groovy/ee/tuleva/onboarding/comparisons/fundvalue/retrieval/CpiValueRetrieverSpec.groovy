package ee.tuleva.onboarding.comparisons.fundvalue.retrieval


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.LocalDate
import java.util.zip.GZIPOutputStream

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CpiValueRetriever.PROVIDER
import static java.nio.charset.StandardCharsets.UTF_8
import static org.assertj.core.api.Assertions.assertThat
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
        """freq,unit,coicop,geo\\TIME_PERIOD\t1996-01 \t1996-02\t1996-03
M,I96,CP00,EE\t93.8 \t96.8\t98.4"""

    server.expect(requestTo("https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/prc_hicp_midx/?format=TSV&compressed=true"))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    LocalDate startDate = LocalDate.of(1996, 1, 1)
    LocalDate endDate = LocalDate.of(1996, 3, 1)
    def result = cpiValueRetriever.retrieveValuesForRange(startDate, endDate)

    then:
    def expected = [
        aFundValue(CpiValueRetriever.KEY, LocalDate.of(1996, 1, 1), 93.8, PROVIDER),
        aFundValue(CpiValueRetriever.KEY, LocalDate.of(1996, 2, 1), 96.8, PROVIDER),
        aFundValue(CpiValueRetriever.KEY, LocalDate.of(1996, 3, 1), 98.4, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
  }

  private byte[] gzip(String input) {
    try (def byteArrayOutputStream = new ByteArrayOutputStream()
         def gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
      gzipOutputStream.write(input.getBytes(UTF_8))
      gzipOutputStream.close()
      return byteArrayOutputStream.toByteArray()
    } catch (IOException e) {
      throw new UncheckedIOException("Error compressing string", e)
    }
  }
}

