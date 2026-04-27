package ee.tuleva.onboarding.comparisons.fundvalue.retrieval

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.Clock
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.CpiValueRetriever.PROVIDER
import static java.nio.charset.StandardCharsets.UTF_8
import static org.assertj.core.api.Assertions.assertThat
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(CpiValueRetriever)
@Import(CpiValueRetrieverSpec.TestClockConfig)
class CpiValueRetrieverSpec extends Specification {

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    Clock clock() {
      return Clock.systemUTC()
    }
  }

  static final String SOURCE_URL =
      "https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/prc_hicp_minr/M.I25.TOTAL.EE/?format=TSV&compressed=true"

  @Autowired
  CpiValueRetriever cpiValueRetriever

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
  }

  def "it is configured for the ECOICOP v2 CPI series"() {
    expect:
    cpiValueRetriever.getKey() == "CPI_ECOICOP2"
    PROVIDER == "EUROSTAT"
  }

  def "it parses the new ECOICOP v2 TSV file and returns raw I25 values"() {
    given:
    def mockApiResponse =
        """freq,unit,coicop18,geo\\TIME_PERIOD\t2025-12 \t2026-01\t2026-02
M,I25,TOTAL,EE\t101.01 \t101.05\t102.78"""

    server.expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    def result = cpiValueRetriever.retrieveValuesForRange(
        LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 1))

    then:
    def expected = [
        aFundValue("CPI_ECOICOP2", LocalDate.of(2025, 12, 1), 101.01, PROVIDER),
        aFundValue("CPI_ECOICOP2", LocalDate.of(2026, 1, 1), 101.05, PROVIDER),
        aFundValue("CPI_ECOICOP2", LocalDate.of(2026, 2, 1), 102.78, PROVIDER)
    ]
    assertThat(result).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
  }

  def "it skips months with the missing-value marker ':'"() {
    given:
    def mockApiResponse =
        """freq,unit,coicop18,geo\\TIME_PERIOD\t1996-01 \t1996-02 \t2026-01
M,I25,TOTAL,EE\t: \t: \t101.05"""

    server.expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    def result = cpiValueRetriever.retrieveValuesForRange(
        LocalDate.of(1996, 1, 1), LocalDate.of(2026, 1, 1))

    then:
    result.size() == 1
    result[0].date() == LocalDate.of(2026, 1, 1)
    result[0].value() == 101.05
  }

  def "it strips Eurostat data-quality flag suffixes from values"() {
    given:
    def mockApiResponse =
        """freq,unit,coicop18,geo\\TIME_PERIOD\t2025-12\t2026-01\t2026-02
M,I25,TOTAL,EE\t101.01 d\t101.05 e\t102.78 p"""

    server.expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    def result = cpiValueRetriever.retrieveValuesForRange(
        LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 1))

    then:
    result*.value() == [101.01, 101.05, 102.78] as List
  }

  def "it filters values to the requested date range"() {
    given:
    def mockApiResponse =
        """freq,unit,coicop18,geo\\TIME_PERIOD\t2025-11\t2025-12\t2026-01\t2026-02
M,I25,TOTAL,EE\t101.25\t101.01\t101.05\t102.78"""

    server.expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    def result = cpiValueRetriever.retrieveValuesForRange(
        LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1))

    then:
    result*.date() == [LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1)]
  }

  def "it throws when the response contains no parseable values"() {
    given:
    def mockApiResponse =
        """freq,unit,coicop18,geo\\TIME_PERIOD\t2025-12\t2026-01
M,I25,TOTAL,EE\t: \t: """

    server.expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(gzip(mockApiResponse), MediaType.TEXT_PLAIN))

    when:
    cpiValueRetriever.retrieveValuesForRange(
        LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1))

    then:
    thrown(EurostatImportException)
  }

  def "it has a 90-day staleness threshold"() {
    expect:
    cpiValueRetriever.stalenessThreshold().toDays() == 90
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

