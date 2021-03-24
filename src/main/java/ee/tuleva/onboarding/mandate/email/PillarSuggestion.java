package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@EqualsAndHashCode
public class PillarSuggestion {

  @Getter private final int suggestedPillar;
  private final boolean isPillarActive;
  private final boolean isPillarFullyConverted;
  private final boolean isMember;

  public PillarSuggestion(
      int suggestedPillar,
      User user,
      UserPreferences contactDetails,
      ConversionResponse conversion) {
    this.suggestedPillar = suggestedPillar;
    isMember = user.isMember();

    if (suggestedPillar == 2) {
      isPillarActive = contactDetails.isSecondPillarActive();
      isPillarFullyConverted = conversion.isSecondPillarFullyConverted();
    } else if (suggestedPillar == 3) {
      isPillarActive = contactDetails.isThirdPillarActive();
      isPillarFullyConverted = conversion.isThirdPillarFullyConverted();
    } else {
      throw new IllegalArgumentException("Unknown pillar: " + suggestedPillar);
    }
  }

  public boolean suggestPillar() {
    return !isPillarActive || !isPillarFullyConverted;
  }

  public boolean suggestMembership() {
    return isPillarActive && isPillarFullyConverted && !isMember;
  }

  public int getOtherPillar() {
    if (suggestedPillar == 2) {
      return 3;
    } else if (suggestedPillar == 3) {
      return 2;
    } else {
      throw new IllegalArgumentException("Unknown pillar: " + suggestedPillar);
    }
  }
}
