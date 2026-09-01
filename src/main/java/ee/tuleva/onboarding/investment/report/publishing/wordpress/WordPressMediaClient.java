package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
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

  private static final String DEFAULT_EXTENSION = "pdf";
  private static final int MAX_BASE_SLUG_LENGTH = 100;

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;

  public record UploadResult(int attachmentId, String sourceUrl) {}

  public UploadResult upload(String filename, byte[] pdfBytes) {
    var slug = toWordPressSlug(filename);

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
    var pageId = findPageIdBySlug(pageSlug);

    retryTemplate.invoke(
        () ->
            restClient
                .post()
                .uri("/pages/{pageId}", pageId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("acf", Map.of("investment_report_file", attachmentId)))
                .retrieve()
                .body(Map.class));

    log.info(
        "Updated ACF investment_report_file: pageSlug={}, pageId={}, attachmentId={}",
        pageSlug,
        pageId,
        attachmentId);
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

  static String toWordPressSlug(String filename) {
    var dotIndex = filename.lastIndexOf('.');
    var base = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    var extension = dotIndex > 0 ? filename.substring(dotIndex + 1) : DEFAULT_EXTENSION;
    var baseSlug = toHyphenatedWords(base);
    if (baseSlug.isEmpty()) {
      throw new IllegalArgumentException(
          "Filename sanitises to an empty slug: filename=" + filename);
    }
    var extensionSlug = toSingleWord(extension);
    return baseSlug + "." + (extensionSlug.isEmpty() ? DEFAULT_EXTENSION : extensionSlug);
  }

  private static String toHyphenatedWords(String value) {
    var hyphenated = asciiSlug(value);
    return hyphenated
        .substring(0, Math.min(hyphenated.length(), MAX_BASE_SLUG_LENGTH))
        .replaceAll("(^-+)|(-+$)", "");
  }

  private static String toSingleWord(String value) {
    return asciiSlug(value).replace("-", "");
  }

  private static String asciiSlug(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-");
  }

  private static String truncate(String s, int maxLen) {
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
  }
}
