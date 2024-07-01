package ee.tuleva.onboarding.payment.provider.montonio;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.springframework.http.MediaType.*;

@Service
public class MontonioApiClient {

  @Value("${payment-provider.url}")
  private String montonioUrl;

  public MontonioApiClient() {
  }

  @SneakyThrows
  public String getPaymentUrl(Map<String, Object> payloadJson) {
    var orderResponse =
        RestClient.create()
            .post()
            .uri(montonioUrl + "/orders")
            .body(payloadJson)
            .accept(APPLICATION_JSON)
            .retrieve()
            .body(MontonioOrderResponse.class);
    return orderResponse.paymentUrl();
  }
}
