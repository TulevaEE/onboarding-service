package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

record MissingSebFundConfirmation(
    String tradeReference,
    TulevaFund fund,
    String isin,
    TransactionType side,
    LocalDate tradeDate,
    BigDecimal quantity) {}
