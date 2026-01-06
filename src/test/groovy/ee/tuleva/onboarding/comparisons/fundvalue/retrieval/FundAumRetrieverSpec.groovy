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

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.PensionikeskusDataDownloader.PROVIDER
import static org.assertj.core.api.Assertions.assertThat
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(components = [FundAumRetriever, PensionikeskusDataDownloader, FundAumRetrieverConfiguration])
class FundAumRetrieverSpec extends Specification {

    @Autowired
    FundAumRetriever secondPillarStockAumRetriever

    @Autowired
    FundAumRetriever secondPillarBondAumRetriever

    @Autowired
    FundAumRetriever thirdPillarAumRetriever

    @Autowired
    MockRestServiceServer server

    def cleanup() {
        server.reset()
    }

    def "should retrieve second pillar stock fund AUM values from CSV"() {
        given:
        def csv = """Date\tFund\tShortname\tISIN\tNet assets\tChange %
2024-11-06\tLHV Pensionifond XL\tLXK75\tEE3600019766\t266999636\t0
2024-11-06\tSEB pension fund 18+\tSEK100\tEE3600001699\t202071126\t0
2024-11-06\tTuleva World Stocks Pension Fund\tTUK75\tEE3600109435\t661127723\t0
"""
        def startDate = LocalDate.parse("2024-11-06")
        def endDate = LocalDate.parse("2024-11-06")
        def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/value-of-assets-of-funded-pension/?f%5B0%5D=77&date_from=06.11.2024&date_to=06.11.2024&download=xls"
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_16)), MediaType.TEXT_PLAIN))

        when:
        List<FundValue> results = secondPillarStockAumRetriever.retrieveValuesForRange(startDate, endDate)

        then:
        def expected = [aFundValue("AUM_EE3600109435", LocalDate.of(2024, 11, 6), 661127723 as BigDecimal, PROVIDER)]
        assertThat(results).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }

    def "should retrieve second pillar bond fund AUM values from CSV"() {
        given:
        def csv = """Date\tFund\tShortname\tISIN\tNet assets\tChange %
2024-11-06\tLHV Pensionifond XL\tLXK75\tEE3600019766\t266999636\t0
2024-11-06\tSEB pension fund 18+\tSEK100\tEE3600001699\t202071126\t0
2024-11-06\tTuleva World Bonds Pension Fund\tTUK00\tEE3600109443\t11327895\t0
"""
        def startDate = LocalDate.parse("2024-11-06")
        def endDate = LocalDate.parse("2024-11-06")
        def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/value-of-assets-of-funded-pension/?f%5B0%5D=76&date_from=06.11.2024&date_to=06.11.2024&download=xls"
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_16)), MediaType.TEXT_PLAIN))

        when:
        List<FundValue> results = secondPillarBondAumRetriever.retrieveValuesForRange(startDate, endDate)

        then:
        def expected = [aFundValue("AUM_EE3600109443", LocalDate.of(2024, 11, 6), 11327895 as BigDecimal, PROVIDER)]
        assertThat(results).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }

    def "should retrieve third pillar fund AUM values from CSV"() {
        given:
        def csv = """Date\tFund\tShortname\tISIN\tNet assets\tChange %
2024-11-06\tLuminor Tulevik 16-50 Pension Fund\tNPT100\tEE3600098422\t29319617\t0
2024-11-06\tTuleva III Pillar Pension Fund\tTUV100\tEE3600001707\t305624105\t0
2024-11-06\tLHV Pensionifond Aktiivne III\tLHT75\tEE3600010294\t31555446\t0
"""
        def startDate = LocalDate.parse("2024-11-06")
        def endDate = LocalDate.parse("2024-11-06")
        def expectedUrl = "https://www.pensionikeskus.ee/en/statistics/iii-pillar/value-of-assets-of-suppl-funded-pension/?f%5B0%5D=81&date_from=06.11.2024&date_to=06.11.2024&download=xls"
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_16)), MediaType.TEXT_PLAIN))

        when:
        List<FundValue> results = thirdPillarAumRetriever.retrieveValuesForRange(startDate, endDate)

        then:
        def expected = [aFundValue("AUM_EE3600001707", LocalDate.of(2024, 11, 6), 305624105 as BigDecimal, PROVIDER)]
        assertThat(results).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(expected)
    }
}
