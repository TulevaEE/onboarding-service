package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;

public class SavingsPaymentFailedEvent extends SavingsPaymentEvent {

  public SavingsPaymentFailedEvent(Object source, User user, Locale locale, PartyId recipient) {
    super(source, user, locale, recipient);
  }
}
