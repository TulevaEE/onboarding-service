package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderCallbackService {

  private final Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations;
  private final UserService userService;

  private final ObjectMapper objectMapper;

  public void processToken(String serializedToken) {

    JWSObject token = parseToken(serializedToken);
    verifyToken(token);

    String serializedInternalReference =
        token.getPayload().toJSONObject().get("merchant_reference").toString();

    BigDecimal amount = new BigDecimal(token.getPayload().toJSONObject().get("amount").toString());

    PaymentReference internalReference = getInternalReference(serializedInternalReference);

    User user = userService.findByPersonalCode(internalReference.getPersonalCode()).orElseThrow();

    Payment.builder()
        .amount(amount)
        .internalReference(internalReference.getUuid())
        .user(user).build();


  }

  private PaymentReference getInternalReference(String serializedInternalReference) {
    try {
      return objectMapper.readValue(serializedInternalReference, PaymentReference.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
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
