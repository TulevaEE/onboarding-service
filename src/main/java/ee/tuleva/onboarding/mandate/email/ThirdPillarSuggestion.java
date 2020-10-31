package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.AllArgsConstructor;
import lombok.ToString;

@ToString
@AllArgsConstructor
public class ThirdPillarSuggestion implements PillarSuggestion {

    private final boolean isSecondPillarActive;
    private final boolean isSecondPillarFullyConverted;
    private final boolean isMember;

    public ThirdPillarSuggestion(User user, UserPreferences contactDetails, ConversionResponse conversion) {
        isSecondPillarActive = contactDetails.isSecondPillarActive();
        isSecondPillarFullyConverted = conversion.isSecondPillarFullyConverted();
        isMember = user.isMember();
    }

    @Override
    public boolean suggestMembershipIfOtherPillarInactive() {
        return !isSecondPillarActive && !isMember;
    }

    @Override
    public boolean suggestMembershipIfOtherPillarFullyConverted() {
        return isSecondPillarFullyConverted && !isMember;
    }

    @Override
    public boolean suggestOtherPillar() {
        return isSecondPillarActive && !isSecondPillarFullyConverted && !isMember;
    }

    @Override
    public int getOtherPillar() {
        return 2;
    }
}
