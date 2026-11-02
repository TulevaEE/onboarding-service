package ee.tuleva.onboarding.investment.transaction.ingest;

@FunctionalInterface
interface ReconciliationAlerts {

  void raise(ReconciliationAlert alert);
}
