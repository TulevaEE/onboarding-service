package ee.tuleva.onboarding.payment.provider.montonio;

import static org.springframework.http.MediaType.*;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MontonioApiClient {

  private final RestClient restClient;

  @Value("${payment-provider.url}")
  private String montonioUrl;

  public MontonioApiClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  public String getPaymentUrl(Map<String, Object> payloadJson) {
    var orderResponse =
        restClient
            .post()
            .uri(ordersUri())
            .body(payloadJson)
            .accept(APPLICATION_JSON)
            .retrieve()
            .body(MontonioOrderResponse.class);

    return orderResponse.paymentUrl();
  }

  private String ordersUri() {
    return UriComponentsBuilder.fromUriString(montonioUrl).path("/orders").build().toUriString();
  }

  public record MontonioOrderResponse(String paymentUrl) {}
}
