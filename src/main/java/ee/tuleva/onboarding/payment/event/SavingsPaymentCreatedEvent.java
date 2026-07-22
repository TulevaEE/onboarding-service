package ee.tuleva.onboarding.payment.event;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;

@Getter
public class SavingsPaymentCreatedEvent extends PaymentEvent {

  private final PartyId recipient;

  public SavingsPaymentCreatedEvent(Object source, User user, Locale locale, PartyId recipient) {
    super(source, user, locale);
    this.recipient = requireNonNull(recipient);
  }

  @Override
  public PaymentData.PaymentType getPaymentType() {
    return PaymentData.PaymentType.SAVINGS;
  }
}
