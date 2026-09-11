package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.party.PartyId;

@FunctionalInterface
public interface SavingsOnboardingCompletion {

  boolean isOnboardingCompleted(PartyId partyId);
}
