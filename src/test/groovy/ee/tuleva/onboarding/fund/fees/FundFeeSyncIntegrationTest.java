package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.manager.FundManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@DataJpaTest
class FundFeeSyncIntegrationTest {

  private static final String II_STATS_URL = "http://pensionikeskus.test/ii/statistics/";
  private static final String III_STATS_URL = "http://pensionikeskus.test/iii/statistics/";
  private static final String II_FEES_URL = "http://pensionikeskus.test/ii/fees/";
  private static final String III_FEES_URL = "http://pensionikeskus.test/iii/fees/";

  @Autowired private TestEntityManager entityManager;
  @Autowired private FundRepository fundRepository;
  @Autowired private FundFeeSyncJob job;
  @Autowired private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    server.reset();
    var fundManager = FundManager.builder().id(1L).name("Tuleva").build();
    persistFund(fundManager, "EE0000000101", "Test Fund Alpha II", 2, "0.0091", "0.0050");
    persistFund(fundManager, "EE0000000102", "Test Fund Beta III", 3, "0.0035", "0.0100");
    persistFund(fundManager, "EE0000000103", "Test Fund Control II", 2, "0.0053", "0.0077");
    entityManager.flush();
  }

  @Test
  void syncsManagementFeesAndOngoingChargesFromPensionikeskus() {
    server
        .expect(requestTo(II_STATS_URL + "?download=xls"))
        .andRespond(
            withSuccess(utf16(secondPillarStatisticsTsv()), MediaType.APPLICATION_OCTET_STREAM));
    server
        .expect(requestTo(III_STATS_URL + "?download=xls"))
        .andRespond(
            withSuccess(utf16(thirdPillarStatisticsTsv()), MediaType.APPLICATION_OCTET_STREAM));
    server
        .expect(requestTo(II_FEES_URL))
        .andRespond(withSuccess(secondPillarFeeComparisonHtml(), MediaType.TEXT_HTML));
    server
        .expect(requestTo(III_FEES_URL))
        .andRespond(withSuccess(thirdPillarFeeComparisonHtml(), MediaType.TEXT_HTML));

    job.syncFees();

    entityManager.flush();
    entityManager.clear();

    assertThat(fundRepository.findByIsin("EE0000000101").getOngoingChargesFigure())
        .isEqualByComparingTo("0.0114");
    assertThat(fundRepository.findByIsin("EE0000000101").getManagementFeeRate())
        .isEqualByComparingTo("0.0085");
    assertThat(fundRepository.findByIsin("EE0000000102").getOngoingChargesFigure())
        .isEqualByComparingTo("0.0055");
    assertThat(fundRepository.findByIsin("EE0000000102").getManagementFeeRate())
        .isEqualByComparingTo("0.0025");
    assertThat(fundRepository.findByIsin("EE0000000103").getOngoingChargesFigure())
        .isEqualByComparingTo("0.0077");
    assertThat(fundRepository.findByIsin("EE0000000103").getManagementFeeRate())
        .isEqualByComparingTo("0.0053");
    server.verify();
  }

  private void persistFund(
      FundManager fundManager,
      String isin,
      String nameEstonian,
      int pillar,
      String managementFeeRate,
      String ongoingChargesFigure) {
    entityManager.persist(
        Fund.builder()
            .isin(isin)
            .nameEstonian(nameEstonian)
            .nameEnglish(nameEstonian)
            .shortName(isin)
            .pillar(pillar)
            .equityShare(BigDecimal.ZERO)
            .managementFeeRate(new BigDecimal(managementFeeRate))
            .ongoingChargesFigure(new BigDecimal(ongoingChargesFigure))
            .status(ACTIVE)
            .fundManager(fundManager)
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build());
  }

  private static byte[] utf16(String content) {
    return content.getBytes(StandardCharsets.UTF_16);
  }

  private static String secondPillarStatisticsTsv() {
    var header =
        String.join(
            "\t",
            "Fond",
            "Kuupäev",
            "NAV",
            "±",
            "3k",
            "6k",
            "1a",
            "3a",
            "5a",
            "10a",
            "15a",
            "20A",
            "AA",
            "Tasud %",
            "Invest. arv",
            "Maht");
    return header
        + "\n"
        + statisticsRow("test fund alpha  ii ", "1,14")
        + "\n"
        + statisticsRow("Test Fund Control II", "0,77")
        + "\n";
  }

  private static String thirdPillarStatisticsTsv() {
    var header =
        String.join(
            "\t",
            "Fond",
            "Seisuga",
            "NAV",
            "±",
            "3k",
            "6k",
            "1a",
            "3a",
            "5a",
            "10a",
            "15a",
            "20A",
            "AA",
            "Tasud %",
            "Osak. arv",
            "Maht");
    return header + "\n" + statisticsRow("Test Fund Beta III", "0,55") + "\n";
  }

  private static String statisticsRow(String fund, String tasud) {
    return String.join(
        "\t",
        fund,
        "10.07.2026",
        "1,50000",
        "0,10",
        "1,00",
        "2,00",
        "3,00",
        "4,00",
        "5,00",
        "0,00",
        "0,00",
        "0,00",
        "6,00",
        tasud,
        "100",
        "10,000");
  }

  private static String secondPillarFeeComparisonHtml() {
    return """
        <html><body>
        <table class="table-stats">
          <thead><tr class="head"><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody class="data">
            <tr class="data"><td class="name"><a href="#">Test Fund Alpha II</a></td><td><span>0,85%</span></td><td><span>1,08%</span></td></tr>
            <tr class="data"><td class="name"><a href="#">Test Fund Control II</a></td><td><span>0,53%</span></td><td><span>0,50%</span></td></tr>
          </tbody>
        </table>
        </body></html>
        """;
  }

  private static String thirdPillarFeeComparisonHtml() {
    return """
        <html><body>
        <table class="table-stats">
          <thead><tr class="head"><th>Fond</th><th>Tagasivõtmistasu</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody class="data">
            <tr class="data"><td class="name"><a href="#">Test Fund Beta III</a></td><td><span>0,00%</span></td><td><span>0,25%</span></td><td><span>0,55%</span></td></tr>
          </tbody>
        </table>
        </body></html>
        """;
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    RestClient.Builder pensionikeskusFeesRestClientBuilder() {
      return RestClient.builder();
    }

    @Bean
    MockRestServiceServer mockRestServiceServer(
        RestClient.Builder pensionikeskusFeesRestClientBuilder) {
      return MockRestServiceServer.bindTo(pensionikeskusFeesRestClientBuilder).build();
    }

    @Bean
    RestClient pensionikeskusFeesRestClient(
        RestClient.Builder pensionikeskusFeesRestClientBuilder,
        MockRestServiceServer mockRestServiceServer) {
      return pensionikeskusFeesRestClientBuilder.build();
    }

    @Bean
    RetryTemplate pensionikeskusFeesRetryTemplate() {
      var policy =
          RetryPolicy.builder()
              .includes(HttpServerErrorException.class, ResourceAccessException.class)
              .excludes(HttpClientErrorException.class)
              .maxRetries(2)
              .delay(Duration.ofMillis(1))
              .multiplier(1)
              .maxDelay(Duration.ofMillis(1))
              .build();
      return new RetryTemplate(policy);
    }

    @Bean
    PensionikeskusFeesProperties pensionikeskusFeesProperties() {
      return new PensionikeskusFeesProperties(
          II_STATS_URL, III_STATS_URL, II_FEES_URL, III_FEES_URL);
    }

    @Bean
    PensionikeskusDailyStatisticsClient pensionikeskusDailyStatisticsClient(
        RestClient pensionikeskusFeesRestClient,
        RetryTemplate pensionikeskusFeesRetryTemplate,
        PensionikeskusFeesProperties pensionikeskusFeesProperties) {
      return new PensionikeskusDailyStatisticsClient(
          pensionikeskusFeesRestClient,
          pensionikeskusFeesRetryTemplate,
          pensionikeskusFeesProperties);
    }

    @Bean
    PensionikeskusFeeComparisonClient pensionikeskusFeeComparisonClient(
        RestClient pensionikeskusFeesRestClient,
        RetryTemplate pensionikeskusFeesRetryTemplate,
        PensionikeskusFeesProperties pensionikeskusFeesProperties) {
      return new PensionikeskusFeeComparisonClient(
          pensionikeskusFeesRestClient,
          pensionikeskusFeesRetryTemplate,
          pensionikeskusFeesProperties);
    }

    @Bean
    FundFeeUpdater fundFeeUpdater(FundRepository fundRepository) {
      return new FundFeeUpdater(fundRepository);
    }

    @Bean
    FundFeeSyncJob fundFeeSyncJob(
        PensionikeskusDailyStatisticsClient pensionikeskusDailyStatisticsClient,
        PensionikeskusFeeComparisonClient pensionikeskusFeeComparisonClient,
        FundFeeUpdater fundFeeUpdater) {
      return new FundFeeSyncJob(
          pensionikeskusDailyStatisticsClient, pensionikeskusFeeComparisonClient, fundFeeUpdater);
    }
  }
}
