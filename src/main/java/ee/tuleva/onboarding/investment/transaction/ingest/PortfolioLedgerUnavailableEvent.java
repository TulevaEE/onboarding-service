package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;

public record PortfolioLedgerUnavailableEvent(
    TulevaFund fund, LocalDate asOfDate, int reportedIsinCount) {}
