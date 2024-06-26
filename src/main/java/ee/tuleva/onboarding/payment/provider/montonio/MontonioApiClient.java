package ee.tuleva.onboarding.payment.provider.montonio;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MontonioApiClient {
  private final RestClient restClient;

  @Value("${payment-provider.url}")
  private String montonioUrl;

  public MontonioApiClient(RestClient.Builder restClientBuilder) {
    this.restClient =
        restClientBuilder.defaultHeader("Accept", "application/json").baseUrl(montonioUrl).build();
  }

  @SneakyThrows
  public String getPaymentUrl(String payloadJson) {
    var orderResponse =
        restClient
            .post()
            .uri("orders")
            .body(payloadJson)
            .retrieve()
            .body(MontonioOrderResponse.class);

    return orderResponse.paymentUrl();
  }
}
