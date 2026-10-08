package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class WordPressPageReader {

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;

  public Optional<String> investmentReportUrl(String pageSlug) {
    return AcfReportField.readFrom(findPage(pageSlug)).attachmentId().map(this::sourceUrl);
  }

  private Map<String, Object> findPage(String slug) {
    var pages =
        retryTemplate.invoke(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/pages")
                                .queryParam("slug", slug)
                                .queryParam("_fields", "id,acf")
                                .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));

    if (pages == null || pages.size() != 1) {
      throw new IllegalStateException(
          "Expected exactly one WordPress page for slug: slug="
              + slug
              + ", count="
              + (pages == null ? 0 : pages.size()));
    }
    return pages.getFirst();
  }

  private String sourceUrl(int attachmentId) {
    var media =
        retryTemplate.invoke(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/media/{attachmentId}")
                                .queryParam("_fields", "source_url")
                                .build(attachmentId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {}));

    if (media == null || !(media.get("source_url") instanceof String sourceUrl)) {
      throw new IllegalStateException(
          "WordPress attachment has no source_url: attachmentId=" + attachmentId);
    }
    return sourceUrl;
  }
}
