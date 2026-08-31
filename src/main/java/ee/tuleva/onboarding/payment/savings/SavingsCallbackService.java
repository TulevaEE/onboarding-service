package ee.tuleva.onboarding.payment.savings;

import static ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService.inferPartyType;
import static java.util.Objects.requireNonNull;

import com.nimbusds.jose.JWSObject;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.IncomingSavingsPayment;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.SavingsPayments;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.payment.provider.PaymentReference;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrderToken;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioTokenParser;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
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
  private final SavingsPayments savingsPayments;
  private final ApplicationEventPublisher eventPublisher;

  @SneakyThrows
  public boolean processToken(String serializedToken) {
    var jwsObject = JWSObject.parse(serializedToken);
    tokenParser.verifyToken(jwsObject, savingsChannelConfiguration.getSecretKey());
    var token = tokenParser.parse(jwsObject);

    var paymentStatus =
        requireNonNull(
            token.getPaymentStatus(),
            "Montonio order token missing payment status: uuid=" + token.getUuid());
    var merchantReference =
        requireNonNull(
            token.getMerchantReference(),
            "Montonio order token missing merchant reference: uuid=" + token.getUuid());

    if (!paymentStatus.equals(MontonioOrderToken.MontonioOrderStatus.PAID)) {
      log.info("Montonio order {} not paid", merchantReference);
      return false;
    }

    if (!merchantReference.getPaymentType().equals(PaymentData.PaymentType.SAVINGS)) {
      log.error("Montonio order {} not SAVINGS type", merchantReference);
      return false;
    }

    var recipient = recipientParty(merchantReference);

    var incomingPayment =
        new IncomingSavingsPayment(
            requireNonNull(
                token.getSenderName(),
                "Montonio order token missing sender name: uuid=" + token.getUuid()),
            requireNonNull(
                token.getSenderIban(),
                "Montonio order token missing sender IBAN: uuid=" + token.getUuid()),
            merchantReference.getDescription(),
            requireNonNull(
                token.getGrandTotal(),
                "Montonio order token missing grand total: uuid=" + token.getUuid()),
            requireNonNull(
                token.getCurrency(),
                "Montonio order token missing currency: uuid=" + token.getUuid()),
            recipient);

    if (!savingsPayments.recordIncoming(incomingPayment)) {
      return false;
    }

    userService
        .findByPersonalCode(merchantReference.getPersonalCode())
        .ifPresent(
            user ->
                eventPublisher.publishEvent(
                    new SavingsPaymentCreatedEvent(
                        this, user, merchantReference.getLocale(), recipient)));

    return true;
  }

  private PartyId recipientParty(PaymentReference ref) {
    var partyType =
        Optional.ofNullable(ref.getRecipientPartyType())
            .orElseGet(() -> inferPartyType(ref.getRecipientPersonalCode()));
    return new PartyId(partyType, ref.getRecipientPersonalCode());
  }
}
