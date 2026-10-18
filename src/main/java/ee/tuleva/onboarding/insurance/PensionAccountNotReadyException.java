package ee.tuleva.onboarding.insurance;

class PensionAccountNotReadyException extends RuntimeException {

  PensionAccountNotReadyException() {
    super("Pension account number not known yet");
  }
}
