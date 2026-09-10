package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class WordPressMediaClient {

  private static final Pattern VALID_WP_PDF_URL =
      Pattern.compile(
          "^https://tuleva\\.ee/wp-content/uploads/\\d{4}/\\d{2}/[A-Za-z0-9._-]+\\.pdf$");

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final List<String> missingProperties;

  public record UploadResult(int attachmentId, String sourceUrl) {}

  public UploadResult upload(String filename, byte[] pdfBytes) {
    refuseWhenUnconfigured();
    var slug = WordPressSlug.of(filename);

    var existing = findExistingMedia(slug);
    if (existing.isPresent()) {
      log.info(
          "Reusing existing WordPress media instead of re-uploading: filename={}, attachmentId={}",
          slug,
          existing.get().attachmentId());
      return existing.get();
    }

    log.info("Uploading PDF to WordPress: filename={}, size={}bytes", slug, pdfBytes.length);

    var response =
        retryTemplate.invoke(
            () ->
                restClient
                    .post()
                    .uri("/media")
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=\"" + slug + "\"")
                    .body(pdfBytes)
                    .retrieve()
                    .body(Map.class));

    if (response == null) {
      throw new IllegalStateException("WordPress returned no response body: filename=" + slug);
    }

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

  private Optional<UploadResult> findExistingMedia(String slug) {
    var media =
        retryTemplate.invoke(
            () ->
                restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/media").queryParam("search", slug).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));

    if (media == null) {
      return Optional.empty();
    }

    return media.stream()
        .flatMap(item -> toUploadResult(item).stream())
        .filter(
            result ->
                VALID_WP_PDF_URL.matcher(result.sourceUrl()).matches()
                    && result.sourceUrl().endsWith("/" + slug))
        .findFirst();
  }

  private static Optional<UploadResult> toUploadResult(Map<String, Object> item) {
    if (item.get("id") instanceof Integer attachmentId
        && item.get("source_url") instanceof String sourceUrl) {
      return Optional.of(new UploadResult(attachmentId, sourceUrl));
    }
    return Optional.empty();
  }

  public void updateAcfReportField(String pageSlug, int attachmentId) {
    refuseWhenUnconfigured();
    var pageId = findPageIdBySlug(pageSlug);

    var response =
        retryTemplate.invoke(
            () ->
                restClient
                    .post()
                    .uri("/pages/{pageId}", pageId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(AcfReportField.pageUpdate(attachmentId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {}));

    assertReportFileStored(pageSlug, pageId, attachmentId, AcfReportField.storedIn(response));

    log.info(
        "Updated ACF investment_report_file: pageSlug={}, pageId={}, attachmentId={}",
        pageSlug,
        pageId,
        attachmentId);
  }

  private void refuseWhenUnconfigured() {
    if (!missingProperties.isEmpty()) {
      throw new IllegalStateException(
          "WordPress report publishing is enabled but not configured: missing="
              + missingProperties);
    }
  }

  private static void assertReportFileStored(
      String pageSlug, int pageId, int attachmentId, AcfReportField storedField) {
    if (!storedField.holds(attachmentId)) {
      throw new IllegalStateException(
          "WordPress accepted the page update but ACF did not store the report file: pageSlug="
              + pageSlug
              + ", pageId="
              + pageId
              + ", expected="
              + attachmentId
              + ", actual="
              + storedField.storedValue());
    }
  }

  private int findPageIdBySlug(String slug) {
    var pages =
        retryTemplate.invoke(
            () ->
                restClient
                    .get()
                    .uri("/pages?slug={slug}", slug)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));

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

    var id = (Integer) pages.getFirst().get("id");
    if (id == null) {
      throw new IllegalStateException("WordPress page missing id: slug=" + slug);
    }
    return id;
  }

  private static String truncate(String s, int maxLen) {
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
  }
}
