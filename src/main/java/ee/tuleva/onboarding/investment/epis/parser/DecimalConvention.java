package ee.tuleva.onboarding.investment.epis.parser;

enum DecimalConvention {
  COMMA_DECIMAL(','),
  PERIOD_DECIMAL('.');

  private final char decimalSeparator;

  DecimalConvention(char decimalSeparator) {
    this.decimalSeparator = decimalSeparator;
  }

  char decimalSeparator() {
    return decimalSeparator;
  }
}
