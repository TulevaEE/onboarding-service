package ee.tuleva.onboarding.accounting.directo;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

public class DirectoClient {

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final DirectoProperties properties;

  DirectoClient(RestClient restClient, RetryTemplate retryTemplate, DirectoProperties properties) {
    this.restClient = restClient;
    this.retryTemplate = retryTemplate;
    this.properties = properties;
  }

  public List<String> entities() {
    return properties.entities();
  }

  public DirectoBook fetchBook(String entity) {
    final String AFTER_THE_LAST_DAY_BEFORE_THE_FIRST_BOOKING = ">2015-12-31T23:59:59";
    String apiKey = properties.apiKey(entity);
    return new DirectoBook(
        fetch("accounts", apiKey, DirectoAccount[].class, uri -> uri.path("/v1/accounts").build()),
        fetch("objects", apiKey, DirectoObject[].class, uri -> uri.path("/v1/objects").build()),
        fetch(
            "transactions",
            apiKey,
            DirectoTransaction[].class,
            uri ->
                uri.path("/v1/transactions")
                    .queryParam("date", AFTER_THE_LAST_DAY_BEFORE_THE_FIRST_BOOKING)
                    .build()));
  }

  private <T> List<T> fetch(
      String resource, String apiKey, Class<T[]> type, Function<UriBuilder, URI> uri) {
    final String KEY_HEADER = "X-Directo-Key";
    try {
      var response =
          retryTemplate.invoke(
              () -> restClient.get().uri(uri).header(KEY_HEADER, apiKey).retrieve().toEntity(type));
      var body = response.getBody();
      if (body == null) {
        throw new DirectoRequestException(resource, response.getStatusCode().value());
      }
      return List.of(body);
    } catch (RestClientResponseException e) {
      throw new DirectoRequestException(resource, e.getStatusCode().value());
    } catch (RestClientException e) {
      throw new DirectoRequestException(resource, NestedExceptionUtils.getMostSpecificCause(e));
    }
  }
}
