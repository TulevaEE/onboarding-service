package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class WordPressMediaClientTest {

  private WordPressMediaClient client;
  private MockRestServiceServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl("https://tuleva.ee/wp-json/wp/v2");
    server = MockRestServiceServer.bindTo(builder).build();
    var configuration = new WordPressConfiguration();
    client =
        new WordPressMediaClient(
            builder.build(),
            configuration.retryingTransientFailures(),
            configuration.retryingRequestsThatNeverReachedWordPress(),
            List.of());
  }

  @Test
  void uploadReturnAttachmentIdAndSourceUrl() throws Exception {
    var responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "id",
                42,
                "source_url",
                "https://tuleva.ee/wp-content/uploads/2026/04/test-report.pdf"));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header("Content-Disposition", "attachment; filename=\"test.pdf\""))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    var result = client.upload("test.pdf", new byte[] {0x25, 0x50, 0x44, 0x46});

    assertThat(result.attachmentId()).isEqualTo(42);
    assertThat(result.sourceUrl())
        .isEqualTo("https://tuleva.ee/wp-content/uploads/2026/04/test-report.pdf");
    server.verify();
  }

  @Test
  void uploadRejectsInvalidSourceUrl() throws Exception {
    var responseBody =
        objectMapper.writeValueAsString(
            Map.of("id", 42, "source_url", "https://evil.com/malicious.pdf"));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.upload("test.pdf", new byte[] {1}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid source_url");
  }

  @Test
  void uploadRejectsMissingAttachmentId() throws Exception {
    var responseBody =
        objectMapper.writeValueAsString(
            Map.of("source_url", "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf"));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.upload("test.pdf", new byte[] {1}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no attachment id");
  }

  @Test
  void aCorrectedReportIsUploadedRatherThanTheFileOfTheSameNameReused() throws Exception {
    var corrected =
        objectMapper.writeValueAsString(
            Map.of(
                "id", 43, "source_url", "https://tuleva.ee/wp-content/uploads/2026/04/test-1.pdf"));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(POST))
        .andRespond(withSuccess(corrected, MediaType.APPLICATION_JSON));

    var result = client.upload("test.pdf", new byte[] {0x25, 0x50, 0x44, 0x46});

    assertThat(result)
        .isEqualTo(new UploadResult(43, "https://tuleva.ee/wp-content/uploads/2026/04/test-1.pdf"));
    server.verify();
  }

  @ParameterizedTest
  @MethodSource("failuresBeforeTheRequestReachedWordPress")
  void anUploadThatNeverReachedWordPressIsRetried(IOException failure) throws Exception {
    var responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "id", 42, "source_url", "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf"));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(POST))
        .andRespond(failingWith(failure));
    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(POST))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    var result = client.upload("test.pdf", new byte[] {0x25, 0x50, 0x44, 0x46});

    assertThat(result)
        .isEqualTo(new UploadResult(42, "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf"));
    server.verify();
  }

  static Stream<IOException> failuresBeforeTheRequestReachedWordPress() {
    return Stream.of(
        new ConnectException("Connection refused"),
        new HttpConnectTimeoutException("HTTP connect timed out"),
        new UnknownHostException("tuleva.ee"));
  }

  @Test
  void anUploadThatTimedOutWaitingForWordPressIsNotRetriedSinceWordPressMayHaveStoredIt() {
    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(POST))
        .andRespond(failingWith(new HttpTimeoutException("request timed out")));

    assertThatThrownBy(() -> client.upload("test.pdf", new byte[] {0x25, 0x50, 0x44, 0x46}))
        .isInstanceOf(ResourceAccessException.class);
    server.verify();
  }

  @Test
  void anUploadWordPressAnsweredWithAServerErrorIsNotRetriedSinceItMayHaveStoredIt() {
    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.upload("test.pdf", new byte[] {0x25, 0x50, 0x44, 0x46}))
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  void aPageLookupThatTimedOutIsRetriedSinceReadingAgainChangesNothing() throws Exception {
    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages?slug=test-page"))
        .andRespond(failingWith(new HttpTimeoutException("request timed out")));
    expectPageLookupAndUpdate(pageWithStoredReportFile(42));

    client.updateAcfReportField("test-page", 42);

    server.verify();
  }

  private static ResponseCreator failingWith(IOException failure) {
    return request -> {
      throw failure;
    };
  }

  @Test
  void updateAcfReportFieldFindsPageAndUpdates() throws Exception {
    expectPageLookupAndUpdate(pageWithStoredReportFile(42));

    client.updateAcfReportField("test-page", 42);

    server.verify();
  }

  @Test
  void updateAcfReportFieldAcceptsAttachmentIdReturnedAsString() throws Exception {
    expectPageLookupAndUpdate(pageWithStoredReportFile("42"));

    client.updateAcfReportField("test-page", 42);

    server.verify();
  }

  @Test
  void updateAcfReportFieldAcceptsAttachmentIdReturnedAsDecimalNumber() throws Exception {
    expectPageLookupAndUpdate(pageWithStoredReportFile(42.0));

    client.updateAcfReportField("test-page", 42);

    server.verify();
  }

  @Test
  void updateAcfReportFieldAcceptsAttachmentIdNestedInAttachmentObject() throws Exception {
    expectPageLookupAndUpdate(
        pageWithStoredReportFile(
            Map.of("ID", 42, "url", "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf")));

    client.updateAcfReportField("test-page", 42);

    server.verify();
  }

  @Test
  void updateAcfReportFieldAcceptsAttachmentIdNestedInAttachmentArray() throws Exception {
    expectPageLookupAndUpdate(pageWithStoredReportFile(List.of(Map.of("id", 42))));

    client.updateAcfReportField("test-page", 42);

    server.verify();
  }

  @Test
  void updateAcfReportFieldThrowsWhenAcfSilentlyDroppedTheWrite() throws Exception {
    expectPageLookupAndUpdate(Map.of("id", 123));

    assertThatThrownBy(() -> client.updateAcfReportField("test-page", 42))
        .isInstanceOf(IllegalStateException.class);

    server.verify();
  }

  @Test
  void updateAcfReportFieldThrowsWhenAcfReportFieldIsEmpty() throws Exception {
    expectPageLookupAndUpdate(pageWithStoredReportFile(""));

    assertThatThrownBy(() -> client.updateAcfReportField("test-page", 42))
        .isInstanceOf(IllegalStateException.class);

    server.verify();
  }

  @Test
  void updateAcfReportFieldThrowsWhenAcfHoldsADifferentAttachment() throws Exception {
    expectPageLookupAndUpdate(pageWithStoredReportFile(41));

    assertThatThrownBy(() -> client.updateAcfReportField("test-page", 42))
        .isInstanceOf(IllegalStateException.class);

    server.verify();
  }

  @Test
  void updateAcfReportFieldThrowsWhenAttachmentObjectCarriesNoAttachmentId() throws Exception {
    expectPageLookupAndUpdate(
        pageWithStoredReportFile(
            Map.of("url", "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf")));

    assertThatThrownBy(() -> client.updateAcfReportField("test-page", 42))
        .isInstanceOf(IllegalStateException.class);

    server.verify();
  }

  @Test
  void updateAcfReportFieldThrowsWhenSlugAmbiguous() throws Exception {
    var pagesResponse =
        objectMapper.writeValueAsString(
            List.of(
                Map.of("id", 123, "slug", "test-page"), Map.of("id", 124, "slug", "test-page")));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages?slug=test-page"))
        .andRespond(withSuccess(pagesResponse, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.updateAcfReportField("test-page", 42))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void updateAcfReportFieldThrowsWhenPageNotFound() throws Exception {
    var emptyResponse = objectMapper.writeValueAsString(List.of());

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages?slug=nonexistent"))
        .andRespond(withSuccess(emptyResponse, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.updateAcfReportField("nonexistent", 42))
        .isInstanceOf(IllegalStateException.class);
  }

  private static Map<String, Object> pageWithStoredReportFile(Object storedReportFile) {
    return Map.of("id", 123, "acf", Map.of("investment_report_file", storedReportFile));
  }

  private void expectPageLookupAndUpdate(Map<String, Object> updateResponse) throws Exception {
    var pagesResponse =
        objectMapper.writeValueAsString(List.of(Map.of("id", 123, "slug", "test-page")));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages?slug=test-page"))
        .andRespond(withSuccess(pagesResponse, MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages/123"))
        .andExpect(method(POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(
            withSuccess(
                objectMapper.writeValueAsString(updateResponse), MediaType.APPLICATION_JSON));
  }
}
