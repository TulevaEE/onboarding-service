package ee.tuleva.onboarding.investment.transaction;

class TransactionBatchRefusedException extends RuntimeException {

  TransactionBatchRefusedException(String message) {
    super(message);
  }
}
