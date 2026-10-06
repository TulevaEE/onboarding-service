package ee.tuleva.onboarding.investment.check.tracking;

class InstrumentRatesNotResolvedException extends IllegalStateException {

  InstrumentRatesNotResolvedException(String message) {
    super(message);
  }
}
