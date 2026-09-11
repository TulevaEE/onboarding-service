package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.PartyId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record IbanWhitelistEntry(
    PartyId partyId, String iban, @Nullable String comment, Instant createdAt) {}
