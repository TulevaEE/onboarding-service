package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Getter
public class PillarSuggestion {

  private final boolean suggestPillar;
  private final boolean suggestMembership;

  public PillarSuggestion(
      int pillar, User user, UserPreferences contactDetails, ConversionResponse conversion) {
    boolean pillarActive;
    boolean pillarFullyConverted;

    if (getSuggestedPillar(pillar) == 2) {
      pillarActive = contactDetails.isSecondPillarActive();
      pillarFullyConverted = conversion.isSecondPillarFullyConverted();
    } else {
      pillarActive = contactDetails.isThirdPillarActive();
      pillarFullyConverted = conversion.isThirdPillarFullyConverted();
    }
    this.suggestPillar = !pillarActive || !pillarFullyConverted;
    this.suggestMembership = pillarActive && pillarFullyConverted && !user.isMember();
  }

  private int getSuggestedPillar(int pillar) {
    return pillar == 2 ? 3 : 2;
  }
}
