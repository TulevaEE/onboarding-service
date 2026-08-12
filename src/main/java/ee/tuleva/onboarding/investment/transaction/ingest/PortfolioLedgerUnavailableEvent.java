package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record PortfolioLedgerUnavailableEvent(
    TulevaFund fund, LocalDate asOfDate, Map<String, BigDecimal> reportedQuantities) {}
