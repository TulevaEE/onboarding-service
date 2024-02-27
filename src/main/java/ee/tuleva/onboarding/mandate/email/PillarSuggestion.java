package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.user.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Getter
public class PillarSuggestion {

  private final boolean suggestPaymentRate;
  private final boolean suggestThirdPillar;
  private final boolean suggestSecondPillar;
  private final boolean suggestMembership;

  public PillarSuggestion(
      User user,
      ContactDetails contactDetails,
      ConversionResponse conversion,
      PaymentRates paymentRates) {
    this.suggestPaymentRate = !paymentRates.hasIncreased();
    this.suggestSecondPillar =
        !contactDetails.isSecondPillarActive() || !conversion.isSecondPillarPartiallyConverted();
    this.suggestThirdPillar =
        !contactDetails.isThirdPillarActive() || !conversion.isThirdPillarPartiallyConverted();
    this.suggestMembership = !user.isMember();
  }
}
