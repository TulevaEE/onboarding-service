package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.AllArgsConstructor;
import lombok.ToString;

@ToString
@AllArgsConstructor
public class ThirdPillarSuggestion implements PillarSuggestion {

    private final boolean isThirdPillarActive;
    private final boolean isThirdPillarFullyConverted;
    private final boolean isMember;

    public ThirdPillarSuggestion(User user, UserPreferences contactDetails, ConversionResponse conversion) {
        isThirdPillarActive = contactDetails.isThirdPillarActive();
        isThirdPillarFullyConverted = conversion.isThirdPillarFullyConverted();
        isMember = user.isMember();
    }

    public boolean showShortMessage() {
        return (isThirdPillarFullyConverted || !isThirdPillarActive) && isMember;
    }

    @Override
    public boolean suggestMembershipIfPillarInactive() {
        return !isThirdPillarActive && !isMember;
    }

    @Override
    public boolean suggestMembershipIfFullyConverted() {
        return isThirdPillarFullyConverted && !isMember;
    }

    @Override
    public boolean suggestPillar() {
        return isThirdPillarActive && !isThirdPillarFullyConverted && !isMember;
    }

    @Override
    public int getPillar() {
        return 3;
    }
}
