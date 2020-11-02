package ee.tuleva.onboarding.mandate.email;

public interface PillarSuggestion {

    boolean suggestMembershipIfPillarInactive();

    boolean suggestMembershipIfFullyConverted();

    boolean suggestPillar();

    int getPillar();

    default boolean suggestMembership() {
        return suggestMembershipIfPillarInactive() || suggestMembershipIfFullyConverted();
    }

}