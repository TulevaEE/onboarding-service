package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.party.PartyId;

public interface SavingsOnboardingCompletion {

  boolean isOnboardingCompleted(PartyId partyId);

  boolean isAccountClosed(PartyId partyId);
}
