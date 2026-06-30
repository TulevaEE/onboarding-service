package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WordPressMediaClientTest {

  private WordPressMediaClient client;
  private MockRestServiceServer server;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl("https://tuleva.ee/wp-json/wp/v2");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new WordPressMediaClient(builder.build());
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
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media?search=test.pdf"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
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
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media?search=test.pdf"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
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
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media?search=test.pdf"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.upload("test.pdf", new byte[] {1}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no attachment id");
  }

  @Test
  void uploadReusesExistingMediaInsteadOfCreatingDuplicate() throws Exception {
    var existing =
        objectMapper.writeValueAsString(
            List.of(
                Map.of(
                    "id",
                    42,
                    "source_url",
                    "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf")));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/media?search=test.pdf"))
        .andRespond(withSuccess(existing, MediaType.APPLICATION_JSON));

    var result = client.upload("test.pdf", new byte[] {0x25, 0x50, 0x44, 0x46});

    assertThat(result.attachmentId()).isEqualTo(42);
    assertThat(result.sourceUrl())
        .isEqualTo("https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    server.verify();
  }

  @Test
  void updateAcfReportFieldFindsPageAndUpdates() throws Exception {
    var pagesResponse =
        objectMapper.writeValueAsString(List.of(Map.of("id", 123, "slug", "test-page")));
    var updateResponse = objectMapper.writeValueAsString(Map.of("id", 123));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages?slug=test-page"))
        .andRespond(withSuccess(pagesResponse, MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages/123"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(withSuccess(updateResponse, MediaType.APPLICATION_JSON));

    client.updateAcfReportField("test-page", 42);

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
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ambiguous WordPress slug");
  }

  @Test
  void updateAcfReportFieldThrowsWhenPageNotFound() throws Exception {
    var emptyResponse = objectMapper.writeValueAsString(List.of());

    server
        .expect(requestTo("https://tuleva.ee/wp-json/wp/v2/pages?slug=nonexistent"))
        .andRespond(withSuccess(emptyResponse, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.updateAcfReportField("nonexistent", 42))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No WordPress page found");
  }

  @Test
  void sanitizeFilenameProducesWordPressSlug() {
    assertThat(WordPressMediaClient.sanitizeFilename("normal.pdf")).isEqualTo("normal.pdf");
    // dangerous and whitespace characters collapse to single hyphens, matching WordPress slugging
    assertThat(WordPressMediaClient.sanitizeFilename("file\"name.pdf")).isEqualTo("file-name.pdf");
    assertThat(WordPressMediaClient.sanitizeFilename("file\\name.pdf")).isEqualTo("file-name.pdf");
    assertThat(WordPressMediaClient.sanitizeFilename("Report 2026-03.pdf"))
        .isEqualTo("report-2026-03.pdf");
    // the real report filename — spaces and capitals slugged exactly as WordPress would store it,
    // so the reuse check finds it on the next run instead of uploading a duplicate
    assertThat(
            WordPressMediaClient.sanitizeFilename(
                "Tuleva Maailma Aktsiate Pensionifondi investeeringute aruanne 2026-03.pdf"))
        .isEqualTo("tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-03.pdf");
    // diacritics are folded to ASCII so the slug is stable
    assertThat(WordPressMediaClient.sanitizeFilename("Võlakirjade fond.pdf"))
        .isEqualTo("volakirjade-fond.pdf");
  }
}
