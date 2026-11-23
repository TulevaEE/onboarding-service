package ee.tuleva.onboarding.investment.transaction.ingest;

record SebFundConfirmationUpload(String key, SebFundConfirmation confirmation) {

  String tradeReference() {
    return confirmation.tradeReference();
  }
}
