package ee.tuleva.onboarding.investment.transaction.ingest;

record BankSettlementRun(int settled, int redated, int withoutBankDate) {}
