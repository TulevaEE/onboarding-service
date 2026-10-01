package ee.tuleva.onboarding.ledger;

enum MirrorOutcome {
  POSTED,
  REVISED,
  REVERSED,
  UNCHANGED,
  QUARANTINED,
  QUARANTINED_WITH_LIVE_VERSION;

  boolean isQuarantined() {
    return this == QUARANTINED || this == QUARANTINED_WITH_LIVE_VERSION;
  }
}
