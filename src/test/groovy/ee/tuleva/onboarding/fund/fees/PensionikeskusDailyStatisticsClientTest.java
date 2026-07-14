package ee.tuleva.onboarding.fund.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class PensionikeskusDailyStatisticsClientTest {

  private static final String II_STATS_URL = "http://pensionikeskus.test/ii/";
  private static final String III_STATS_URL = "http://pensionikeskus.test/iii/";
  private static final String II_DOWNLOAD_URL = II_STATS_URL + "?download=xls";
  private static final String III_DOWNLOAD_URL = III_STATS_URL + "?download=xls";

  private MockRestServiceServer server;
  private PensionikeskusDailyStatisticsClient client;

  private void setUp() {
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    var properties =
        new PensionikeskusFeesProperties(
            II_STATS_URL, III_STATS_URL, "http://unused/", "http://unused/");
    client =
        new PensionikeskusDailyStatisticsClient(builder.build(), fastRetryTemplate(), properties);
  }

  @Test
  void parsesSecondPillarTsvResolvingTasudColumnByHeaderName() {
    setUp();
    var tsv =
        secondPillarHeader()
            + "\n"
            + statisticsRow("LHV Pensionifond Ettevõtlik", "1,57")
            + "\n"
            + statisticsRow("Tuleva Maailma Aktsiate Pensionifond", "0,28")
            + "\n";
    respondWithTsv(II_DOWNLOAD_URL, tsv);

    var result = client.fetchOngoingCharges(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new PensionikeskusFeeRow("LHV Pensionifond Ettevõtlik", new BigDecimal("0.0157")),
                new PensionikeskusFeeRow(
                    "Tuleva Maailma Aktsiate Pensionifond", new BigDecimal("0.0028"))));
    server.verify();
  }

  @Test
  void parsesThirdPillarHeaderVariant() {
    setUp();
    var tsv =
        thirdPillarHeader() + "\n" + statisticsRow("Tuleva III Samba Pensionifond", "0,28") + "\n";
    respondWithTsv(III_DOWNLOAD_URL, tsv);

    var result = client.fetchOngoingCharges(3);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new PensionikeskusFeeRow(
                    "Tuleva III Samba Pensionifond", new BigDecimal("0.0028"))));
    server.verify();
  }

  @Test
  void parsesBothCommaAndDotDecimals() {
    setUp();
    var tsv =
        secondPillarHeader()
            + "\n"
            + statisticsRow("Comma Fund", "1,14")
            + "\n"
            + statisticsRow("Dot Fund", "0.53")
            + "\n";
    respondWithTsv(II_DOWNLOAD_URL, tsv);

    var result = client.fetchOngoingCharges(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new PensionikeskusFeeRow("Comma Fund", new BigDecimal("0.0114")),
                new PensionikeskusFeeRow("Dot Fund", new BigDecimal("0.0053"))));
    server.verify();
  }

  @Test
  void skipsRowsWithMissingOrUnparseableTasudValue() {
    setUp();
    var tsv =
        secondPillarHeader()
            + "\n"
            + statisticsRow("Valid Fund", "1,00")
            + "\n"
            + statisticsRow("Empty Fund", "")
            + "\n"
            + statisticsRow("Dash Fund", "-")
            + "\n"
            + "Short Fund\t10.07.2026\t1,50000\n";
    respondWithTsv(II_DOWNLOAD_URL, tsv);

    var result = client.fetchOngoingCharges(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(List.of(new PensionikeskusFeeRow("Valid Fund", new BigDecimal("0.0100"))));
    server.verify();
  }

  @Test
  void throwsOnUnsupportedPillar() {
    setUp();

    assertThatThrownBy(() -> client.fetchOngoingCharges(4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenTasudColumnMissingFromHeader() {
    setUp();
    var header =
        String.join("\t", "Fond", "Kuupäev", "NAV", "±", "3k", "6k", "1a", "Invest. arv", "Maht");
    respondWithTsv(
        II_DOWNLOAD_URL, header + "\n" + "Some Fund\t10.07.2026\t1,0\t0,1\t1\t2\t3\t4\t5\n");

    assertThatThrownBy(() -> client.fetchOngoingCharges(2))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void throwsWhenResponseHasNoDataRows() {
    setUp();
    respondWithTsv(II_DOWNLOAD_URL, secondPillarHeader() + "\n");

    assertThatThrownBy(() -> client.fetchOngoingCharges(2))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void throwsWhenResponseIsHtmlErrorPageServedWith200() {
    setUp();
    respondWithTsv(II_DOWNLOAD_URL, "<html><body>Service temporarily unavailable</body></html>");

    assertThatThrownBy(() -> client.fetchOngoingCharges(2))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void retriesOn5xxAndSucceeds() {
    setUp();
    server.expect(requestTo(II_DOWNLOAD_URL)).andRespond(withServerError());
    server
        .expect(requestTo(II_DOWNLOAD_URL))
        .andRespond(
            withSuccess(
                utf16(secondPillarHeader() + "\n" + statisticsRow("Valid Fund", "1,00") + "\n"),
                MediaType.APPLICATION_OCTET_STREAM));

    var result = client.fetchOngoingCharges(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(List.of(new PensionikeskusFeeRow("Valid Fund", new BigDecimal("0.0100"))));
    server.verify();
  }

  @Test
  void doesNotRetryOn4xx() {
    setUp();
    server.expect(requestTo(II_DOWNLOAD_URL)).andRespond(withBadRequest());

    assertThatThrownBy(() -> client.fetchOngoingCharges(2))
        .isInstanceOf(HttpClientErrorException.class);
    server.verify();
  }

  private void respondWithTsv(String url, String tsv) {
    server
        .expect(requestTo(url))
        .andRespond(withSuccess(utf16(tsv), MediaType.APPLICATION_OCTET_STREAM));
  }

  private static byte[] utf16(String content) {
    return content.getBytes(StandardCharsets.UTF_16);
  }

  private static String secondPillarHeader() {
    return String.join(
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
  }

  private static String thirdPillarHeader() {
    return String.join(
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

  private static RetryTemplate fastRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(3)
            .delay(Duration.ofMillis(1))
            .multiplier(1)
            .maxDelay(Duration.ofMillis(1))
            .build();
    return new RetryTemplate(policy);
  }
}
