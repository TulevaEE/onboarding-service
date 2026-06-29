package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;

record TransactionMatchingProperties(
    BigDecimal etfQuantityTolerance,
    BigDecimal fundBuyAmountTolerance,
    BigDecimal fundSellQuantityTolerance,
    BigDecimal nearMissMultiplier,
    BigDecimal executionPriceConsistencyTolerance) {

  TransactionMatchingProperties {
    etfQuantityTolerance =
        etfQuantityTolerance == null ? new BigDecimal("0.0001") : etfQuantityTolerance;
    fundBuyAmountTolerance =
        fundBuyAmountTolerance == null ? new BigDecimal("0.02") : fundBuyAmountTolerance;
    fundSellQuantityTolerance =
        fundSellQuantityTolerance == null ? new BigDecimal("0.0001") : fundSellQuantityTolerance;
    nearMissMultiplier = nearMissMultiplier == null ? new BigDecimal("5") : nearMissMultiplier;
    executionPriceConsistencyTolerance =
        executionPriceConsistencyTolerance == null
            ? new BigDecimal("0.01")
            : executionPriceConsistencyTolerance;
  }
}
