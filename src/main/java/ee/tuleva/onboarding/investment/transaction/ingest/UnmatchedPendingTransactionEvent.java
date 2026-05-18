package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;

record UnmatchedPendingTransactionEvent(SebPendingTransactionRow row, LocalDate reportDate) {}
