package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.provider.PaymentProviderChannel;
import ee.tuleva.onboarding.payment.provider.PaymentProviderConfiguration;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MontonioOrderClient {

  private final ObjectMapper objectMapper;

  private final MontonioApiClient montonioApiClient;

  private final PaymentProviderConfiguration paymentProviderConfiguration;

  @SneakyThrows
  public String getPaymentUrl(MontonioOrder order, PaymentData paymentData) {
    var payload = getSignedOrderPayload(order, paymentData);
    return montonioApiClient.getPaymentUrl(payload);
  }

  @SneakyThrows
  private Map<String, Object> getSignedOrderPayload(MontonioOrder order, PaymentData paymentData) {
    PaymentProviderChannel paymentChannelConfiguration =
        paymentProviderConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel());

    JWSObject jwsObject =
        getSignedJws(objectMapper.writeValueAsString(order), paymentChannelConfiguration);

    return Map.of("data", jwsObject.serialize());
  }

  @SneakyThrows
  private JWSObject getSignedJws(
      String payload, PaymentProviderChannel paymentChannelConfiguration) {
    JWSObject jwsObject =
        new JWSObject(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
            new Payload(payload));

    jwsObject.sign(new MACSigner(paymentChannelConfiguration.getSecretKey().getBytes()));
    return jwsObject;
  }
}
