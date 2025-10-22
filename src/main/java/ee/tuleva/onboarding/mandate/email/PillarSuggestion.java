package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
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
    this.suggestPaymentRate = paymentRates.canIncrease();
    this.suggestSecondPillar =
        !contactDetails.isSecondPillarActive()
            || !conversion.isSecondPillarPartiallyConverted()
            || (conversion.getSecondPillarWeightedAverageFee() != null
                && conversion.getSecondPillarWeightedAverageFee().compareTo(new BigDecimal("0.005"))
                    > 0);
    this.suggestThirdPillar =
        !contactDetails.isThirdPillarActive()
            || !conversion.isThirdPillarPartiallyConverted()
            || (conversion.getThirdPillarWeightedAverageFee() != null
                && conversion.getThirdPillarWeightedAverageFee().compareTo(new BigDecimal("0.005"))
                    > 0);
    this.suggestMembership = !user.isMember();
  }
}
