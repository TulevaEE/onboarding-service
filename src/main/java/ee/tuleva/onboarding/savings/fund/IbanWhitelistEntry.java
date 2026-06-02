package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.PartyId;
import java.time.Instant;

public record IbanWhitelistEntry(PartyId partyId, String iban, String comment, Instant createdAt) {}
