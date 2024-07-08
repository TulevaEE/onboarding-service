package ee.tuleva.onboarding.payment.provider.montonio;

import static ee.tuleva.onboarding.currency.Currency.EUR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentRepository;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import ee.tuleva.onboarding.payment.provider.PaymentReference;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MontonioCallbackService {

  private final MontonioPaymentChannelConfiguration montonioPaymentChannelConfiguration;
  private final UserService userService;
  private final PaymentRepository paymentRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  @SneakyThrows
  public Optional<Payment> processToken(String serializedToken) {
    // TODO: can we create a separate class for the token and encapsulate the verify() and isFinalized() logic there?
    JWSObject token = JWSObject.parse(serializedToken);
    verifyToken(token);

    Map<String, Object> json = token.getPayload().toJSONObject();
    String serializedInternalReference = json.get("merchantReference").toString();
    BigDecimal amount = new BigDecimal(json.get("grandTotal").toString());

    PaymentReference internalReference = getInternalReference(serializedInternalReference);

    if (!isPaymentFinalized(token)) {
      return Optional.empty();
    }

    Optional<Payment> existingPayment =
        paymentRepository.findByInternalReference(internalReference.getUuid());

    if (existingPayment.isPresent()) {
      return existingPayment;
    }

    User user = userService.findByPersonalCode(internalReference.getPersonalCode()).orElseThrow();

    Payment paymentToBeSaved =
        Payment.builder()
            .amount(amount)
            .currency(EUR)
            .internalReference(internalReference.getUuid())
            .user(user)
            .recipientPersonalCode(internalReference.getRecipientPersonalCode())
            .paymentType(internalReference.getPaymentType())
            .build();

    Payment payment = paymentRepository.save(paymentToBeSaved);
    eventPublisher.publishEvent(
        new PaymentCreatedEvent(this, user, payment, internalReference.getLocale()));

    return Optional.of(payment);
  }

  private boolean isPaymentFinalized(JWSObject token) {
    return token.getPayload().toJSONObject().get("paymentStatus").toString().equals("PAID");
  }

  @SneakyThrows
  private PaymentReference getInternalReference(String serializedInternalReference) {
    return objectMapper.readValue(serializedInternalReference, PaymentReference.class);
  }

  @SneakyThrows
  private void verifyToken(JWSObject token) {
    String accessKey = token.getPayload().toJSONObject().get("accessKey").toString();
    MontonioPaymentChannel paymentChannelConfiguration =
        montonioPaymentChannelConfiguration.getPaymentProviderChannel(accessKey);

    if (!token.verify(new MACVerifier(paymentChannelConfiguration.getSecretKey().getBytes()))) {
      throw new BadCredentialsException("Token not verified");
    }
  }
}
