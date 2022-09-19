package ee.tuleva.onboarding.payment;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderCallbackService {

  private final Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations;

  public void processToken(String serializedToken) {

    JWSObject token = parseToken(serializedToken);
    verifyToken(token);
  }

  private void verifyToken(JWSObject token) {
    PaymentProviderBankConfiguration bankConfiguration = getPaymentProviderBankConfiguration(token);

    try {
      if(!token.verify(new MACVerifier(bankConfiguration.secretKey.getBytes()))) {
        throw new BadCredentialsException("Token not verified");
      }
    } catch (JOSEException e) {
      throw new RuntimeException(e);
    }
  }

  private PaymentProviderBankConfiguration getPaymentProviderBankConfiguration(JWSObject token) {
    String bic = token.getPayload().toJSONObject().get("preselected_aspsp").toString();

    return paymentProviderBankConfigurations
        .values().stream().filter(conf -> conf.bic.equals(bic)).findFirst().orElseThrow();
  }

  private JWSObject parseToken(String serializedToken) {
    try {
      return JWSObject.parse(serializedToken);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

}
