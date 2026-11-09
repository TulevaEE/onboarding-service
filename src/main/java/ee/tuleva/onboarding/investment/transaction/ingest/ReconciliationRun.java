package ee.tuleva.onboarding.investment.transaction.ingest;

record ReconciliationRun(
    ReconciliationMode mode, ReconciliationAlerts alerts, SebReplayListings listings)
    implements ReconciliationAlerts {

  @Override
  public void raise(ReconciliationAlert alert) {
    alerts.raise(alert);
  }
}
