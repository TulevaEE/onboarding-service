package ee.tuleva.onboarding.investment.transaction.ingest;

record BankSettlementRun(int settled, int sameDate, int newDate, int withoutBankDate) {}
