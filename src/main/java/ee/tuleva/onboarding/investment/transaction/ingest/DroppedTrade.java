package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;

record DroppedTrade(TransactionOrder order, String reference, LocalDate settlementDate) {}
