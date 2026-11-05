package ee.tuleva.onboarding.investment.transaction.ingest;

record ReconciliationRun(ReconciliationMode mode, ReconciliationAlerts alerts) {

  void raise(ReconciliationAlert alert) {
    alerts.raise(alert);
  }
}
