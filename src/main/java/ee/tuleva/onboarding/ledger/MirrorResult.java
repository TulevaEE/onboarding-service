package ee.tuleva.onboarding.ledger;

public record MirrorResult(
    int posted,
    int revised,
    int reversed,
    int unchanged,
    int quarantined,
    int quarantinedWithLiveVersion) {}
