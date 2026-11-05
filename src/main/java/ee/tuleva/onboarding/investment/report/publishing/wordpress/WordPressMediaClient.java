package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.PageUpdateFailed.LeftUnchanged;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.PageUpdateFailed.LinkUnknown;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.PageUpdateFailed.NotLinked;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class WordPressMediaClient {

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final RetryTemplate uploadRetryTemplate;
  private final List<String> missingProperties;

  public record UploadResult(int attachmentId, String sourceUrl) {

    private static final Pattern VALID_WP_PDF_URL =
        Pattern.compile(
            "^https://tuleva\\.ee/wp-content/uploads/\\d{4}/\\d{2}/[A-Za-z0-9._-]+\\.pdf$");

    private static UploadResult readFrom(@Nullable Map<?, ?> response, String slug) {
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
      return new UploadResult(attachmentId, sourceUrl);
    }

    private static String truncate(String s, int maxLen) {
      return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
  }

  public UploadResult upload(String filename, byte[] pdfBytes) {
    refuseWhenUnconfigured();
    var slug = WordPressSlug.of(filename);

    log.info("Uploading PDF to WordPress: filename={}, size={}bytes", slug, pdfBytes.length);

    try {
      var response =
          uploadRetryTemplate.invoke(
              () ->
                  restClient
                      .post()
                      .uri("/media")
                      .contentType(MediaType.APPLICATION_PDF)
                      .header("Content-Disposition", "attachment; filename=\"" + slug + "\"")
                      .body(pdfBytes)
                      .retrieve()
                      .body(Map.class));
      var uploaded = UploadResult.readFrom(response, slug);
      log.info(
          "WordPress upload successful: attachmentId={}, sourceUrl={}",
          uploaded.attachmentId(),
          uploaded.sourceUrl());
      return uploaded;
    } catch (RuntimeException failure) {
      throw UploadFailed.after(slug, failure);
    }
  }

  public void updateAcfReportField(
      String pageSlug, int attachmentId, BooleanSupplier publishLockStillHeld) {
    refuseWhenUnconfigured();
    var pageId = findPageIdBySlug(pageSlug);

    var attempts = new PageUpdateAttempts();
    try {
      var response =
          retryTemplate.invoke(
              () -> {
                if (!publishLockStillHeld.getAsBoolean()) {
                  throw attempts.lockLost(pageSlug);
                }
                return attempts.send(
                    () ->
                        restClient
                            .post()
                            .uri("/pages/{pageId}", pageId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(AcfReportField.pageUpdate(attachmentId))
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
              });
      assertReportFileStored(pageSlug, pageId, attachmentId, AcfReportField.storedIn(response));
    } catch (PublishLockLost lockLost) {
      settleAfterTheLockWasLost(pageSlug, attachmentId, attempts, lockLost);
      return;
    } catch (RuntimeException failure) {
      settleAFailedUpdate(pageSlug, attachmentId, attempts, failure);
      return;
    }

    log.info(
        "Updated ACF investment_report_file: pageSlug={}, pageId={}, attachmentId={}",
        pageSlug,
        pageId,
        attachmentId);
  }

  private void settleAfterTheLockWasLost(
      String pageSlug, int attachmentId, PageUpdateAttempts attempts, PublishLockLost lockLost) {
    if (attempts.noneSent()) {
      log.error("Fund page left unchanged, the publish lost its lock: pageSlug={}", pageSlug);
      throw new LeftUnchanged(pageSlug, lockLost);
    }
    settleAFailedUpdate(pageSlug, attachmentId, attempts, lockLost);
  }

  private void settleAFailedUpdate(
      String pageSlug, int attachmentId, PageUpdateAttempts attempts, RuntimeException failure) {
    boolean linked;
    try {
      linked = AcfReportField.readFrom(findPageBySlug(pageSlug)).holds(attachmentId);
    } catch (RuntimeException readBack) {
      failure.addSuppressed(readBack);
      log.error(
          "Fund page update failed and could not be read back: pageSlug={}", pageSlug, failure);
      throw new LinkUnknown(pageSlug, failure);
    }
    if (linked) {
      log.warn(
          "Fund page update failed but the page links the report: pageSlug={}", pageSlug, failure);
      return;
    }
    log.error(
        "Fund page update failed: pageSlug={}, uncertain={}",
        pageSlug,
        attempts.anyUncertain(),
        failure);
    throw attempts.anyUncertain()
        ? new LinkUnknown(pageSlug, failure)
        : new NotLinked(pageSlug, failure);
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
    var id = (Integer) findPageBySlug(slug).get("id");
    if (id == null) {
      throw new IllegalStateException("WordPress page missing id: slug=" + slug);
    }
    return id;
  }

  private Map<String, Object> findPageBySlug(String slug) {
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
    return pages.getFirst();
  }
}
