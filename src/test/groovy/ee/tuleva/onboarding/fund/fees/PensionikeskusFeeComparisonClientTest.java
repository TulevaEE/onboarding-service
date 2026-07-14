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

class PensionikeskusFeeComparisonClientTest {

  private static final String II_FEES_URL = "http://pensionikeskus.test/ii/fees/";
  private static final String III_FEES_URL = "http://pensionikeskus.test/iii/fees/";
  private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

  private MockRestServiceServer server;
  private PensionikeskusFeeComparisonClient client;

  private void setUp() {
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    var properties =
        new PensionikeskusFeesProperties(
            "http://unused/", "http://unused/", II_FEES_URL, III_FEES_URL);
    client =
        new PensionikeskusFeeComparisonClient(builder.build(), fastRetryTemplate(), properties);
  }

  @Test
  void parsesSecondPillarFeeTableResolvingValitsemistasuByHeaderText() {
    setUp();
    var html =
        """
        <html><body>
        <table class="table-stats">
          <thead><tr class="head"><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody class="data">
            <tr class="data"><td class="name"><a href="#">LHV Pensionifond Ettevõtlik</a></td><td><span>0,6000%</span></td><td><span title="2025">1,57%</span></td></tr>
            <tr class="data"><td class="name"><a href="#">Tuleva Maailma Võlakirjade Pensionifond</a></td><td><span>0,163%</span></td><td><span>0,28%</span></td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    var result = client.fetchManagementFees(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new PensionikeskusFeeRow("LHV Pensionifond Ettevõtlik", new BigDecimal("0.006000")),
                new PensionikeskusFeeRow(
                    "Tuleva Maailma Võlakirjade Pensionifond", new BigDecimal("0.00163"))));
    server.verify();
  }

  @Test
  void parsesThirdPillarTableWithExtraRedemptionFeeColumn() {
    setUp();
    var html =
        """
        <html><body>
        <table class="table-stats">
          <thead><tr class="head"><th>Fond</th><th>Tagasivõtmistasu</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody class="data">
            <tr class="data"><td class="name"><a href="#">Tuleva III Samba Pensionifond</a></td><td><span>0,00%</span></td><td><span>0,25%</span></td><td><span>0,55%</span></td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(III_FEES_URL, html);

    var result = client.fetchManagementFees(3);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new PensionikeskusFeeRow(
                    "Tuleva III Samba Pensionifond", new BigDecimal("0.0025"))));
    server.verify();
  }

  @Test
  void parsesBothCommaAndDotDecimals() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
            <tr><td><a href="#">Comma Fund</a></td><td>0,85%</td><td>1,08%</td></tr>
            <tr><td><a href="#">Dot Fund</a></td><td>0.53%</td><td>0.50%</td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    var result = client.fetchManagementFees(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new PensionikeskusFeeRow("Comma Fund", new BigDecimal("0.0085")),
                new PensionikeskusFeeRow("Dot Fund", new BigDecimal("0.0053"))));
    server.verify();
  }

  @Test
  void skipsRowsWithUnparseableValues() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
            <tr><td><a href="#">Valid Fund</a></td><td>0,85%</td><td>1,08%</td></tr>
            <tr><td><a href="#">Broken Fund</a></td><td>N/A</td><td>1,08%</td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    var result = client.fetchManagementFees(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(List.of(new PensionikeskusFeeRow("Valid Fund", new BigDecimal("0.0085"))));
    server.verify();
  }

  @Test
  void handlesNonBreakingSpacesAndWhitespaceInHeadersAndCells() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond&nbsp;</th><th>&nbsp;Valitsemistasu&nbsp;</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
            <tr><td><a href="#">Spaced Fund</a></td><td><span>0,85&nbsp;%</span></td><td>1,08%</td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    var result = client.fetchManagementFees(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(List.of(new PensionikeskusFeeRow("Spaced Fund", new BigDecimal("0.0085"))));
    server.verify();
  }

  @Test
  void skipsRowsWithFootnoteMarkersAfterPercentValue() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
            <tr><td><a href="#">Valid Fund</a></td><td>0,85%</td><td>1,08%</td></tr>
            <tr><td><a href="#">Footnote Fund</a></td><td>0,50% 1</td><td>1,08%</td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    var result = client.fetchManagementFees(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(List.of(new PensionikeskusFeeRow("Valid Fund", new BigDecimal("0.0085"))));
    server.verify();
  }

  @Test
  void throwsOnUnsupportedPillar() {
    setUp();

    assertThatThrownBy(() -> client.fetchManagementFees(4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenNoTableWithValitsemistasuHeader() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
            <tr><td><a href="#">Some Fund</a></td><td>1,08%</td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    assertThatThrownBy(() -> client.fetchManagementFees(2))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void throwsWhenTableHasNoDataRows() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
          </tbody>
        </table>
        </body></html>
        """;
    respondWithHtml(II_FEES_URL, html);

    assertThatThrownBy(() -> client.fetchManagementFees(2))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void retriesOn5xxAndSucceeds() {
    setUp();
    var html =
        """
        <html><body>
        <table>
          <thead><tr><th>Fond</th><th>Valitsemistasu</th><th>Jooksvad tasud</th></tr></thead>
          <tbody>
            <tr><td><a href="#">Valid Fund</a></td><td>0,85%</td><td>1,08%</td></tr>
          </tbody>
        </table>
        </body></html>
        """;
    server.expect(requestTo(II_FEES_URL)).andRespond(withServerError());
    server.expect(requestTo(II_FEES_URL)).andRespond(withSuccess(html, HTML_UTF8));

    var result = client.fetchManagementFees(2);

    assertThat(result)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(List.of(new PensionikeskusFeeRow("Valid Fund", new BigDecimal("0.0085"))));
    server.verify();
  }

  @Test
  void doesNotRetryOn4xx() {
    setUp();
    server.expect(requestTo(II_FEES_URL)).andRespond(withBadRequest());

    assertThatThrownBy(() -> client.fetchManagementFees(2))
        .isInstanceOf(HttpClientErrorException.class);
    server.verify();
  }

  private void respondWithHtml(String url, String html) {
    server.expect(requestTo(url)).andRespond(withSuccess(html, HTML_UTF8));
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
