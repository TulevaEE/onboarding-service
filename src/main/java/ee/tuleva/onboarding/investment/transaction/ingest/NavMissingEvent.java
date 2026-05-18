package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.LocalDate;

record NavMissingEvent(Long executionId, String isin, LocalDate tradeDate) {}
