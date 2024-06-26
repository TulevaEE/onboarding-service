package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
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
// @AllArgsConstructor
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
  private String getSignedOrderPayload(MontonioOrder order, PaymentData paymentData) {
    PaymentProviderChannel paymentChannelConfiguration =
        paymentProviderConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel());

    JWSObject jwsObject =
        getSignedJws(objectMapper.writeValueAsString(order), paymentChannelConfiguration);

    return objectMapper.writeValueAsString(Map.of("data", jwsObject.toString()));
  }

  @SneakyThrows
  private JWSObject getSignedJws(
      String payload, PaymentProviderChannel paymentChannelConfiguration) {
    JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload));
    jwsObject.sign(new MACSigner(paymentChannelConfiguration.getSecretKey().getBytes()));
    return jwsObject;
  }
}
