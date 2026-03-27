package ee.tuleva.onboarding.payment.savings;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import com.nimbusds.jose.JWSObject;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.payment.provider.PaymentReference;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrderToken;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsCallbackService {

  private final UserService userService;
  private final MontonioTokenParser tokenParser;
  private final SavingsChannelConfiguration savingsChannelConfiguration;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final ApplicationEventPublisher eventPublisher;

  @SneakyThrows
  public Optional<SavingFundPayment> processToken(String serializedToken) {
    var jwsObject = JWSObject.parse(serializedToken);
    tokenParser.verifyToken(jwsObject, savingsChannelConfiguration.getSecretKey());
    var token = tokenParser.parse(jwsObject);

    if (!token.getPaymentStatus().equals(MontonioOrderToken.MontonioOrderStatus.PAID)) {
      log.info("Montonio order {} not paid", token.getMerchantReference());
      return Optional.empty();
    }

    if (!token.getMerchantReference().getPaymentType().equals(PaymentData.PaymentType.SAVINGS)) {
      log.error("Montonio order {} not SAVINGS type", token.getMerchantReference());
      return Optional.empty();
    }

    if (!savingFundPaymentRepository
        .findRecentPayments(token.getMerchantReference().getDescription())
        .isEmpty()) {
      log.info("Saving fund payment already exists for {}", token.getMerchantReference());
      return Optional.empty();
    }

    var payment =
        SavingFundPayment.builder()
            .remitterName(token.getSenderName())
            .remitterIban(token.getSenderIban())
            .description(token.getMerchantReference().getDescription())
            .amount(token.getGrandTotal())
            .currency(token.getCurrency())
            .build();

    var paymentId = savingFundPaymentRepository.savePaymentData(payment);
    var ref = token.getMerchantReference();

    attachRecipientParty(paymentId, ref);

    userService
        .findByPersonalCode(ref.getPersonalCode())
        .ifPresent(
            user ->
                eventPublisher.publishEvent(
                    new SavingsPaymentCreatedEvent(this, user, ref.getLocale())));

    return Optional.of(payment);
  }

  private void attachRecipientParty(UUID paymentId, PaymentReference ref) {
    var partyType = Optional.ofNullable(ref.getRecipientPartyType()).orElse(PERSON);
    var partyId = new PartyId(partyType, ref.getRecipientPersonalCode());
    savingFundPaymentRepository.attachParty(paymentId, partyId);
  }
}
