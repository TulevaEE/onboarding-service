package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transaction-matching")
record TransactionMatchingProperties(
    BigDecimal etfQuantityTolerance,
    BigDecimal fundBuyAmountTolerance,
    BigDecimal fundSellQuantityTolerance,
    BigDecimal nearMissMultiplier) {

  TransactionMatchingProperties {
    etfQuantityTolerance =
        etfQuantityTolerance == null ? new BigDecimal("0.0001") : etfQuantityTolerance;
    fundBuyAmountTolerance =
        fundBuyAmountTolerance == null ? new BigDecimal("0.02") : fundBuyAmountTolerance;
    fundSellQuantityTolerance =
        fundSellQuantityTolerance == null ? new BigDecimal("0.0001") : fundSellQuantityTolerance;
    nearMissMultiplier = nearMissMultiplier == null ? new BigDecimal("5") : nearMissMultiplier;
  }
}
