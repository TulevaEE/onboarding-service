package ee.tuleva.onboarding.payment.provider.montonio;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class MontonioTokenParser {

  private final JsonMapper objectMapper;
  private final MontonioPaymentChannelConfiguration montonioPaymentChannelConfiguration;

  @SneakyThrows
  public MontonioOrderToken parse(JWSObject token) {
    return objectMapper.readValue(token.getPayload().toString(), MontonioOrderToken.class);
  }

  @SneakyThrows
  public void verifyToken(JWSObject token) {
    String accessKey = token.getPayload().toJSONObject().get("accessKey").toString();
    MontonioPaymentChannel paymentChannelConfiguration =
        montonioPaymentChannelConfiguration.getPaymentProviderChannel(accessKey);
    verifyToken(token, paymentChannelConfiguration.getSecretKey());
  }

  @SneakyThrows
  public void verifyToken(JWSObject token, String secretKey) {
    if (!token.verify(new MACVerifier(secretKey.getBytes()))) {
      throw new BadCredentialsException("Token not verified");
    }
  }
}
