package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;

public class SavingsPaymentCreatedEvent extends SavingsPaymentEvent {

  public SavingsPaymentCreatedEvent(Object source, User user, Locale locale, PartyId recipient) {
    super(source, user, locale, recipient);
  }
}
