package ee.tuleva.onboarding.mandate.email;

public interface PillarSuggestion {

    boolean suggestMembershipIfOtherPillarInactive();

    boolean suggestMembershipIfOtherPillarFullyConverted();

    boolean suggestOtherPillar();

    int getOtherPillar();

    default boolean suggestMembership() {
        return suggestMembershipIfOtherPillarInactive() || suggestMembershipIfOtherPillarFullyConverted();
    }

}