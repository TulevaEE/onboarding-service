package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class WordPressMediaClient {

  private static final Pattern VALID_WP_PDF_URL =
      Pattern.compile(
          "^https://tuleva\\.ee/wp-content/uploads/\\d{4}/\\d{2}/[A-Za-z0-9._-]+\\.pdf$");

  private final RestClient restClient;

  public record UploadResult(int attachmentId, String sourceUrl) {}

  public UploadResult upload(String filename, byte[] pdfBytes) {
    var sanitizedFilename = sanitizeFilename(filename);
    log.info(
        "Uploading PDF to WordPress: filename={}, size={}bytes",
        sanitizedFilename,
        pdfBytes.length);

    @SuppressWarnings("unchecked")
    var response =
        restClient
            .post()
            .uri("/media")
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=\"" + sanitizedFilename + "\"")
            .body(pdfBytes)
            .retrieve()
            .body(Map.class);

    var sourceUrl = (String) response.get("source_url");
    if (sourceUrl == null || !VALID_WP_PDF_URL.matcher(sourceUrl).matches()) {
      throw new IllegalStateException(
          "WordPress returned invalid source_url: " + truncate(String.valueOf(sourceUrl), 200));
    }

    var attachmentId = (Integer) response.get("id");
    if (attachmentId == null) {
      throw new IllegalStateException("WordPress returned no attachment id");
    }

    log.info("WordPress upload successful: attachmentId={}, sourceUrl={}", attachmentId, sourceUrl);
    return new UploadResult(attachmentId, sourceUrl);
  }

  public void updateAcfReportField(String pageSlug, int attachmentId) {
    var pageId = findPageIdBySlug(pageSlug);

    restClient
        .post()
        .uri("/pages/{pageId}", pageId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("acf", Map.of("investment_report_file", attachmentId)))
        .retrieve()
        .body(Map.class);

    log.info(
        "Updated ACF investment_report_file: pageSlug={}, pageId={}, attachmentId={}",
        pageSlug,
        pageId,
        attachmentId);
  }

  private int findPageIdBySlug(String slug) {
    var pages =
        restClient
            .get()
            .uri("/pages?slug={slug}", slug)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    if (pages == null || pages.isEmpty()) {
      throw new IllegalStateException("No WordPress page found with slug: " + slug);
    }
    if (pages.size() > 1) {
      throw new IllegalStateException(
          "Ambiguous WordPress slug matched multiple pages: slug="
              + slug
              + ", count="
              + pages.size());
    }

    return (Integer) pages.getFirst().get("id");
  }

  static String sanitizeFilename(String filename) {
    return filename.replaceAll("[\\\\\"\\r\\n]", "_");
  }

  private static String truncate(String s, int maxLen) {
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
  }
}
