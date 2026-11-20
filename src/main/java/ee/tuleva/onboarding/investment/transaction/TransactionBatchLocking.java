package ee.tuleva.onboarding.investment.transaction;

interface TransactionBatchLocking {

  void lockAgainstConcurrentConfirmation(TransactionBatch batch);

  void lockAgainstConcurrentChange(TransactionBatch batch);
}
