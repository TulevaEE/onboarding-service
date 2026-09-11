package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_CASH_MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_NOT_ACHIEVED;
import static ee.tuleva.onboarding.investment.transaction.LimitStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.SCALE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.transaction.CalculationWarning;
import ee.tuleva.onboarding.investment.transaction.FundCalculationResult;
import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.LimitStatus;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import ee.tuleva.onboarding.investment.transaction.TradeCalculation;
import ee.tuleva.onboarding.investment.transaction.TransactionMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TradeCalculationEngine {

  public FundCalculationResult calculate(FundTransactionInput input, TransactionMode mode) {
    validateInput(input);

    BigDecimal netInvestable = netInvestable(input);
    Map<String, BigDecimal> weightMap = buildWeightMap(input.modelWeights());

    List<BigDecimal> rawTrades =
        switch (mode) {
          case BUY -> BuyAllocation.calculateBuy(input, netInvestable, weightMap);
          case SELL -> SellPath.calculateSell(input, netInvestable, weightMap);
          case SELL_FAST -> SellPath.calculateSellFast(input, netInvestable, weightMap);
          case REBALANCE -> RebalancePath.calculateRebalance(input, netInvestable, weightMap);
        };

    List<TradeCalculation> trades = applyLimits(input, rawTrades);
    return new FundCalculationResult(
        input.fund(),
        mode,
        input,
        trades,
        netInvestable,
        noTradeReason(input, mode, trades),
        warnings(input, mode, trades, netInvestable, weightMap));
  }

  private void validateInput(FundTransactionInput input) {
    input
        .positions()
        .forEach(
            position -> {
              if (position.marketValue().signum() < 0) {
                throw new IllegalArgumentException(
                    "Position market value cannot be negative: fund=%s, isin=%s, marketValue=%s"
                        .formatted(
                            input.fund().name(),
                            position.isin(),
                            position.marketValue().toPlainString()));
              }
            });
    input
        .modelWeights()
        .forEach(
            weight -> {
              if (weight.weight().signum() < 0) {
                throw new IllegalArgumentException(
                    "Model weight cannot be negative: fund=%s, isin=%s, weight=%s"
                        .formatted(
                            input.fund().name(), weight.isin(), weight.weight().toPlainString()));
              }
            });
    if (input.grossPortfolioValue().signum() < 0) {
      throw new IllegalArgumentException(
          "Gross portfolio value cannot be negative: fund=%s, grossPortfolioValue=%s"
              .formatted(input.fund().name(), input.grossPortfolioValue().toPlainString()));
    }
  }

  private List<CalculationWarning> warnings(
      FundTransactionInput input,
      TransactionMode mode,
      List<TradeCalculation> trades,
      BigDecimal netInvestable,
      Map<String, BigDecimal> weightMap) {
    if (mode != TransactionMode.REBALANCE || input.positions().isEmpty()) {
      return List.of();
    }
    return rebalanceWarnings(input, trades, netInvestable, weightMap);
  }

  private List<CalculationWarning> rebalanceWarnings(
      FundTransactionInput input,
      List<TradeCalculation> trades,
      BigDecimal netInvestable,
      Map<String, BigDecimal> weightMap) {
    RebalancePath.RebalanceBuckets buckets =
        RebalancePath.rebalanceBuckets(input, netInvestable, weightMap);
    BigDecimal intendedNet = sum(buckets.buyScores()).subtract(sum(buckets.sellScores()));
    BigDecimal realizedNet =
        trades.stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    BigDecimal threshold = input.minTransactionThreshold();

    List<CalculationWarning> warnings = new ArrayList<>();
    BigDecimal cashGap = intendedNet.subtract(input.freeCash());
    if (cashGap.abs().compareTo(threshold) >= 0) {
      warnings.add(
          new CalculationWarning(
              REBALANCE_NET_CASH_MISMATCH,
              ("Rebalance net cash need does not match free cash, check the inputs: fund=%s,"
                      + " intendedNet=%s, freeCash=%s, difference=%s, minTransactionThreshold=%s")
                  .formatted(
                      input.fund().name(),
                      intendedNet.toPlainString(),
                      input.freeCash().toPlainString(),
                      cashGap.toPlainString(),
                      threshold.toPlainString())));
    }

    BigDecimal achievedGap = realizedNet.subtract(intendedNet);
    if (achievedGap.abs().compareTo(threshold) >= 0) {
      warnings.add(
          new CalculationWarning(
              REBALANCE_NET_NOT_ACHIEVED,
              ("Rebalance could not achieve the intended net, a limit or the minimum trade size"
                      + " blocked part of it and a Sell may be needed instead: fund=%s,"
                      + " realizedNet=%s, intendedNet=%s, difference=%s,"
                      + " minTransactionThreshold=%s")
                  .formatted(
                      input.fund().name(),
                      realizedNet.toPlainString(),
                      intendedNet.toPlainString(),
                      achievedGap.toPlainString(),
                      threshold.toPlainString())));
    }

    warnings.forEach(warning -> log.warn(warning.message()));
    return List.copyOf(warnings);
  }

  private static BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(ZERO, BigDecimal::add);
  }

  @Nullable
  private String noTradeReason(
      FundTransactionInput input, TransactionMode mode, List<TradeCalculation> trades) {
    boolean hasTrades = trades.stream().anyMatch(trade -> trade.tradeAmount().compareTo(ZERO) != 0);
    if (hasTrades) {
      return null;
    }
    return switch (mode) {
      case BUY -> buyNoTradeReason(input);
      case SELL, SELL_FAST -> sellNoTradeReason(input, mode);
      case REBALANCE -> "No trades: mode=REBALANCE, reason=noDriftBeyondThreshold";
    };
  }

  private String buyNoTradeReason(FundTransactionInput input) {
    if (input.positions().isEmpty()) {
      return "No trades: mode=BUY, reason=noPositions";
    }
    if (input.freeCash().compareTo(input.minTransactionThreshold()) < 0) {
      return "No trades: mode=BUY, reason=freeCashBelowMinTransactionThreshold, freeCash=%s,"
              .formatted(input.freeCash().toPlainString())
          + " minTransactionThreshold=%s"
              .formatted(input.minTransactionThreshold().toPlainString());
    }
    return "No trades: mode=BUY, reason=noPositionUnderTargetBeyondThreshold";
  }

  private String sellNoTradeReason(FundTransactionInput input, TransactionMode mode) {
    if (input.freeCash().compareTo(new BigDecimal("-0.01")) >= 0) {
      return "No trades: mode=%s, reason=noCashShortfall, freeCash=%s"
          .formatted(mode.name(), input.freeCash().toPlainString());
    }
    return "No trades: mode=%s, reason=shortfallBelowMinTransactionThreshold, freeCash=%s,"
            .formatted(mode.name(), input.freeCash().toPlainString())
        + " minTransactionThreshold=%s".formatted(input.minTransactionThreshold().toPlainString());
  }

  private List<TradeCalculation> applyLimits(
      FundTransactionInput input, List<BigDecimal> rawTrades) {
    List<TradeCalculation> trades = new ArrayList<>(rawTrades.size());
    for (int i = 0; i < rawTrades.size(); i++) {
      trades.add(applyLimitToTrade(input, input.positions().get(i), rawTrades.get(i)));
    }
    return List.copyOf(trades);
  }

  private TradeCalculation applyLimitToTrade(
      FundTransactionInput input, PositionSnapshot position, BigDecimal tradeAmount) {
    BigDecimal projectedMarketValue = position.marketValue().add(tradeAmount);
    BigDecimal projectedWeight =
        input.grossPortfolioValue().compareTo(ZERO) > 0
            ? projectedMarketValue.divide(input.grossPortfolioValue(), 6, HALF_UP)
            : ZERO;

    LimitStatus limitStatus = OK;
    var limits = input.positionLimits().get(position.isin());

    if (limits != null) {
      if (projectedWeight.compareTo(limits.hardLimit()) > 0) {
        if (tradeAmount.compareTo(ZERO) > 0) {
          BigDecimal maxAllowedMarketValue =
              input
                  .grossPortfolioValue()
                  .multiply(limits.hardLimit().subtract(new BigDecimal("0.0001")));
          BigDecimal maxTrade = maxAllowedMarketValue.subtract(position.marketValue());
          tradeAmount = maxTrade.max(ZERO).setScale(SCALE, HALF_UP);

          projectedMarketValue = position.marketValue().add(tradeAmount);
          projectedWeight = projectedMarketValue.divide(input.grossPortfolioValue(), 6, HALF_UP);
        }
        limitStatus = LimitStatus.HARD_LIMIT_EXCEEDED;
      } else if (projectedWeight.compareTo(limits.softLimit()) > 0) {
        limitStatus = LimitStatus.SOFT_LIMIT_EXCEEDED;
      }
    }

    return new TradeCalculation(position.isin(), tradeAmount, projectedWeight, limitStatus);
  }

  private BigDecimal netInvestable(FundTransactionInput input) {
    return input
        .grossPortfolioValue()
        .subtract(input.cashBuffer())
        .subtract(input.liabilities())
        .add(input.receivables());
  }

  private Map<String, BigDecimal> buildWeightMap(List<ModelWeight> modelWeights) {
    return modelWeights.stream()
        .collect(toMap(ModelWeight::isin, ModelWeight::weight, (a, b) -> b));
  }
}
