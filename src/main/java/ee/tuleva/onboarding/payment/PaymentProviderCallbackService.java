package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderCallbackService {

  private final PaymentProviderConfiguration paymentProviderConfiguration;
  private final UserService userService;
  private final PaymentRepository paymentRepository;
  private final ObjectMapper objectMapper;

  @SneakyThrows
  public void processToken(String serializedToken) {

    JWSObject token = JWSObject.parse(serializedToken);
    verifyToken(token);

    String serializedInternalReference =
        token.getPayload().toJSONObject().get("merchant_reference").toString();

    BigDecimal amount = new BigDecimal(token.getPayload().toJSONObject().get("amount").toString());

    PaymentReference internalReference = getInternalReference(serializedInternalReference);

    if (noPaymentWithReference(internalReference) && isPaymentFinalized(token)) {
      User user = userService.findByPersonalCode(internalReference.getPersonalCode()).orElseThrow();

      Payment paymentToBeSaved =
          Payment.builder()
              .amount(amount)
              .internalReference(internalReference.getUuid())
              .user(user)
              .status(PaymentStatus.PENDING)
              .build();

      paymentRepository.save(paymentToBeSaved);
    }
  }

  private boolean noPaymentWithReference(PaymentReference internalReference) {
    return paymentRepository.findByInternalReference(internalReference.getUuid()).isEmpty();
  }

  private boolean isPaymentFinalized(JWSObject token) {
    return token.getPayload().toJSONObject().get("status").toString().equals("finalized");
  }

  @SneakyThrows
  private PaymentReference getInternalReference(String serializedInternalReference) {
    return objectMapper.readValue(serializedInternalReference, PaymentReference.class);
  }

  @SneakyThrows
  private void verifyToken(JWSObject token) {
    String accessKey = token.getPayload().toJSONObject().get("access_key").toString();
    PaymentProviderBank bankConfiguration =
        paymentProviderConfiguration.getPaymentProviderBank(accessKey);

    if (!token.verify(new MACVerifier(bankConfiguration.secretKey.getBytes()))) {
      throw new BadCredentialsException("Token not verified");
    }
  }
}
