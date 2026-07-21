package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;

@Getter
public class SavingsPaymentCreatedEvent extends PaymentEvent {

  private final String recipientPersonalCode;
  private final PartyId.Type recipientPartyType;

  public SavingsPaymentCreatedEvent(
      Object source,
      User user,
      Locale locale,
      String recipientPersonalCode,
      PartyId.Type recipientPartyType) {
    super(source, user, locale);
    this.recipientPersonalCode = recipientPersonalCode;
    this.recipientPartyType = recipientPartyType;
  }

  @Override
  public PaymentData.PaymentType getPaymentType() {
    return PaymentData.PaymentType.SAVINGS;
  }
}
