package ee.tuleva.onboarding.payment.provider.montonio;

import static org.springframework.http.MediaType.*;

import java.util.Map;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MontonioApiClient {

  @Value("${payment-provider.url}")
  private String montonioUrl;

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

  public record MontonioOrderResponse(String paymentUrl) {
  }
}
