package ee.tuleva.onboarding.savings.fund.documents;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
class WordpressDocumentsClient {

  private static final String EXPECTED_HOST = "tuleva.ee";
  private static final String EXPECTED_PATH_PREFIX = "/wp-content/uploads/";

  private final RestClient restClient;
  private final WordpressDocumentsConfiguration configuration;
  private final RetryTemplate retryTemplate;

  WordpressDocumentsClient(
      RestClient.Builder restClientBuilder,
      WordpressDocumentsConfiguration configuration,
      RetryTemplate wordpressDocumentsRetryTemplate) {
    this.restClient = restClientBuilder.baseUrl(configuration.getUrl()).build();
    this.configuration = configuration;
    this.retryTemplate = wordpressDocumentsRetryTemplate;
  }

  SavingsFundDocuments fetch() {
    return retryTemplate.invoke(() -> toDocuments(fetchPages()));
  }

  private WordpressPage[] fetchPages() {
    log.info("Fetching savings fund documents from WordPress: slug={}", configuration.getSlug());
    return restClient
        .get()
        .uri("/pages?slug={slug}", configuration.getSlug())
        .accept(APPLICATION_JSON)
        .retrieve()
        .body(WordpressPage[].class);
  }

  private SavingsFundDocuments toDocuments(WordpressPage[] pages) {
    if (pages == null || pages.length == 0 || pages[0].acf() == null) {
      throw new WordpressDocumentsException(
          "WordPress fund documents page returned no ACF data: slug=" + configuration.getSlug());
    }
    Acf acf = pages[0].acf();
    return new SavingsFundDocuments(
        validatedPdfUrl(acf.terms(), "terms_file"),
        validatedPdfUrl(acf.prospectus(), "prospectus_file"),
        validatedPdfUrl(acf.keyInformation(), "key_investor_info_file"));
  }

  private String validatedPdfUrl(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new WordpressDocumentsException("Missing WordPress document field: field=" + field);
    }
    URI uri;
    try {
      uri = new URI(value);
    } catch (Exception e) {
      throw new WordpressDocumentsException(
          "Invalid WordPress document URL: field=" + field + ", value=" + value);
    }
    boolean valid =
        "https".equals(uri.getScheme())
            && EXPECTED_HOST.equals(uri.getHost())
            && uri.getRawPath() != null
            && uri.getRawPath().startsWith(EXPECTED_PATH_PREFIX)
            && uri.getRawPath().endsWith(".pdf")
            && uri.getRawQuery() == null
            && uri.getRawFragment() == null;
    if (!valid) {
      throw new WordpressDocumentsException(
          "Invalid WordPress document URL: field=" + field + ", value=" + value);
    }
    return value;
  }

  record WordpressPage(Acf acf) {}

  record Acf(
      @JsonProperty("terms_file") String terms,
      @JsonProperty("prospectus_file") String prospectus,
      @JsonProperty("key_investor_info_file") String keyInformation) {}
}
