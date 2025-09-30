package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import ee.tuleva.onboarding.payment.PaymentData;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MontonioOrderClient {

  private final ObjectMapper objectMapper;

  private final MontonioApiClient montonioApiClient;

  private final MontonioPaymentChannelConfiguration montonioPaymentChannelConfiguration;

  String getPaymentUrl(MontonioOrder order, PaymentData paymentData) {
    MontonioPaymentChannel paymentChannel =
        montonioPaymentChannelConfiguration.getPaymentProviderChannel(
            paymentData.getPaymentChannel());
    return getPaymentUrl(order, paymentChannel);
  }

  public String getPaymentUrl(MontonioOrder order, MontonioPaymentChannel paymentChannel) {
    var payload = getSignedOrderPayload(order, paymentChannel);
    return montonioApiClient.getPaymentUrl(payload);
  }

  @SneakyThrows
  private Map<String, Object> getSignedOrderPayload(
      MontonioOrder order, MontonioPaymentChannel paymentChannel) {
    JWSObject jwsObject = getSignedJws(objectMapper.writeValueAsString(order), paymentChannel);
    return Map.of("data", jwsObject.serialize());
  }

  @SneakyThrows
  private JWSObject getSignedJws(
      String payload, MontonioPaymentChannel paymentChannelConfiguration) {
    JWSObject jwsObject =
        new JWSObject(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
            new Payload(payload));

    jwsObject.sign(new MACSigner(paymentChannelConfiguration.getSecretKey().getBytes()));
    return jwsObject;
  }
}
