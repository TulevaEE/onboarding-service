package ee.tuleva.onboarding.ledger;

import org.jspecify.annotations.Nullable;

public record UnclassifiedEntryDetails(
    @Nullable String counterpartyName,
    @Nullable String counterpartyIban,
    @Nullable String remittanceInformation,
    @Nullable String subFamilyCode) {}
