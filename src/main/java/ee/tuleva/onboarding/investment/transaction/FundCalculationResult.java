package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record FundCalculationResult(
    TulevaFund fund,
    TransactionMode mode,
    FundTransactionInput input,
    List<TradeCalculation> trades,
    BigDecimal netInvestable,
    @Nullable String noTradeReason,
    List<CalculationWarning> warnings) {}
