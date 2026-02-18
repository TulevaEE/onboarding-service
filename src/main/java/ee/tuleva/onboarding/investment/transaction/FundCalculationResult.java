package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.List;

public record FundCalculationResult(
    TulevaFund fund,
    TransactionMode mode,
    FundTransactionInput input,
    List<TradeCalculation> trades) {}
