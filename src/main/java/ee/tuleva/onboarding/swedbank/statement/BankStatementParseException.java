package ee.tuleva.onboarding.swedbank.statement;

public class BankStatementParseException extends RuntimeException {

  public BankStatementParseException(String message) {
    super(message);
  }

  public BankStatementParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
