package ee.tuleva.onboarding.investment.transaction.ingest;

enum BankSettlementOutcome {
  ALREADY_FROM_BANK,
  SETTLED,
  SAME_DATE,
  NEW_DATE,
  NO_BANK_DATE,
  FAILED
}
