package ee.tuleva.onboarding.investment.transaction;

public class TransactionBatchRefusedException extends RuntimeException {

  public TransactionBatchRefusedException(String message) {
    super(message);
  }
}
