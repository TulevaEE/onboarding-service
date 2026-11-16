package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Page;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.DisclosureGap.Problem;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class WordPressPageReader {

  private static final String DISCLOSURE_GAPS = "tuleva_disclosure_gaps";

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;

  public Optional<String> investmentReportUrl(String pageSlug) {
    return AcfReportField.readFrom(findPage(pageSlug, "id,acf"))
        .attachmentId()
        .map(this::sourceUrl);
  }

  public List<DisclosureGap> disclosureGaps(String pageSlug) {
    var page = findPage(pageSlug, "id," + DISCLOSURE_GAPS);
    if (!(page.get(DISCLOSURE_GAPS) instanceof List<?> gaps)) {
      throw new IllegalStateException(
          "WordPress page does not expose its disclosure gaps over REST: field="
              + DISCLOSURE_GAPS
              + ", pageSlug="
              + pageSlug
              + ", pageId="
              + page.get("id"));
    }
    return gaps.stream().map(gap -> disclosureGap(pageSlug, gap)).toList();
  }

  private static DisclosureGap disclosureGap(String pageSlug, Object gap) {
    if (gap instanceof Map<?, ?> listed && listed.get("field") instanceof String field) {
      var problem = named(Problem.class, listed.get("problem"));
      var page = named(Page.class, listed.get("page"));
      if (problem.isPresent() && page.isPresent()) {
        return new DisclosureGap(field, problem.get(), page.get());
      }
    }
    throw new IllegalStateException(
        "WordPress page lists a disclosure gap this reader does not know: pageSlug="
            + pageSlug
            + ", gap="
            + gap);
  }

  private static <E extends Enum<E>> Optional<E> named(Class<E> type, @Nullable Object wireValue) {
    return EnumSet.allOf(type).stream()
        .filter(constant -> constant.name().toLowerCase(Locale.ROOT).equals(wireValue))
        .findFirst();
  }

  private Map<String, Object> findPage(String slug, String fields) {
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
                                .queryParam("_fields", fields)
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
