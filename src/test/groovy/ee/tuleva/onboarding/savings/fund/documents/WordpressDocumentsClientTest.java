package ee.tuleva.onboarding.savings.fund.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@SpringJUnitConfig(classes = WordpressDocumentsClientTest.TestConfig.class)
class WordpressDocumentsClientTest {

  private static final String URL =
      "https://tuleva.ee/wp-json/wp/v2/pages?slug=tuleva-taiendav-kogumisfond-dokumendid";

  @Autowired WordpressDocumentsClient client;
  @Autowired MockRestServiceServer mockServer;

  @BeforeEach
  void resetMockServer() {
    mockServer.reset();
  }

  @Test
  void fetch_mapsAcfFieldsToDomainDocuments() {
    mockServer
        .expect(requestTo(URL))
        .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

    SavingsFundDocuments documents = client.fetch();

    assertThat(documents)
        .isEqualTo(
            new SavingsFundDocuments(
                "https://tuleva.ee/wp-content/uploads/2026/06/terms.pdf",
                "https://tuleva.ee/wp-content/uploads/2026/06/prospectus.pdf",
                "https://tuleva.ee/wp-content/uploads/2026/06/key-info.pdf"));
    mockServer.verify();
  }

  @Test
  void fetch_throwsWhenUrlHostIsNotTuleva() {
    String body =
        """
        [{"acf": {
          "terms_file": "https://evil.example.com/wp-content/uploads/2026/06/terms.pdf",
          "prospectus_file": "https://tuleva.ee/wp-content/uploads/2026/06/prospectus.pdf",
          "key_investor_info_file": "https://tuleva.ee/wp-content/uploads/2026/06/key-info.pdf"
        }}]
        """;
    mockServer.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch()).isInstanceOf(WordpressDocumentsException.class);
  }

  @Test
  void fetch_throwsWhenUrlIsNotPdf() {
    String body =
        """
        [{"acf": {
          "terms_file": "https://tuleva.ee/wp-content/uploads/2026/06/terms.docx",
          "prospectus_file": "https://tuleva.ee/wp-content/uploads/2026/06/prospectus.pdf",
          "key_investor_info_file": "https://tuleva.ee/wp-content/uploads/2026/06/key-info.pdf"
        }}]
        """;
    mockServer.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch()).isInstanceOf(WordpressDocumentsException.class);
  }

  @Test
  void fetch_throwsWhenFieldMissing() {
    String body =
        """
        [{"acf": {
          "prospectus_file": "https://tuleva.ee/wp-content/uploads/2026/06/prospectus.pdf",
          "key_investor_info_file": "https://tuleva.ee/wp-content/uploads/2026/06/key-info.pdf"
        }}]
        """;
    mockServer.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch()).isInstanceOf(WordpressDocumentsException.class);
  }

  @Test
  void fetch_throwsWhenPageArrayEmpty() {
    mockServer.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch()).isInstanceOf(WordpressDocumentsException.class);
  }

  @Test
  void fetch_retriesOnServerErrorThenSucceeds() {
    mockServer.expect(requestTo(URL)).andRespond(withServerError());
    mockServer.expect(requestTo(URL)).andRespond(withServerError());
    mockServer
        .expect(requestTo(URL))
        .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

    SavingsFundDocuments documents = client.fetch();

    assertThat(documents.terms())
        .isEqualTo("https://tuleva.ee/wp-content/uploads/2026/06/terms.pdf");
    mockServer.verify();
  }

  @Test
  void fetch_doesNotRetryOnClientError() {
    mockServer
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withBadRequest());

    assertThatThrownBy(() -> client.fetch()).isInstanceOf(HttpClientErrorException.class);
    mockServer.verify();
  }

  private static String validResponse() {
    return """
        [{"acf": {
          "terms_file": "https://tuleva.ee/wp-content/uploads/2026/06/terms.pdf",
          "prospectus_file": "https://tuleva.ee/wp-content/uploads/2026/06/prospectus.pdf",
          "key_investor_info_file": "https://tuleva.ee/wp-content/uploads/2026/06/key-info.pdf"
        }}]
        """;
  }

  @Configuration
  static class TestConfig {

    @Bean
    RestClient.Builder wordpressDocumentsRestClientBuilder() {
      return RestClient.builder();
    }

    @Bean
    MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
      return MockRestServiceServer.bindTo(builder).build();
    }

    @Bean
    WordpressDocumentsConfiguration wordpressDocumentsConfiguration() {
      return new WordpressDocumentsConfiguration();
    }

    @Bean
    RetryTemplate wordpressDocumentsRetryTemplate() {
      return new RetryTemplate(
          RetryPolicy.builder()
              .includes(HttpServerErrorException.class, ResourceAccessException.class)
              .excludes(HttpClientErrorException.class)
              .maxRetries(3)
              .delay(Duration.ofMillis(1))
              .multiplier(1)
              .maxDelay(Duration.ofMillis(1))
              .build());
    }

    @Bean
    WordpressDocumentsClient wordpressDocumentsClient(
        RestClient.Builder builder,
        MockRestServiceServer mockServer,
        WordpressDocumentsConfiguration configuration,
        RetryTemplate wordpressDocumentsRetryTemplate) {
      return new WordpressDocumentsClient(builder, configuration, wordpressDocumentsRetryTemplate);
    }
  }
}
