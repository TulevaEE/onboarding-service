package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionMatchingPropertiesTest {

  @Test
  void nullValuesDefaultToCurrentTolerances() {
    TransactionMatchingProperties properties =
        new TransactionMatchingProperties(null, null, null, null, null);

    assertThat(properties.etfQuantityTolerance()).isEqualByComparingTo("0.0001");
    assertThat(properties.fundBuyAmountTolerance()).isEqualByComparingTo("0.02");
    assertThat(properties.fundSellQuantityTolerance()).isEqualByComparingTo("0.0001");
    assertThat(properties.nearMissMultiplier()).isEqualByComparingTo("5");
    assertThat(properties.executionPriceConsistencyTolerance()).isEqualByComparingTo("0.01");
  }

  @Test
  void explicitValuesAreRetained() {
    TransactionMatchingProperties properties =
        new TransactionMatchingProperties(
            new BigDecimal("0.001"),
            new BigDecimal("0.05"),
            new BigDecimal("0.002"),
            new BigDecimal("3"),
            new BigDecimal("0.03"));

    assertThat(properties.etfQuantityTolerance()).isEqualByComparingTo("0.001");
    assertThat(properties.fundBuyAmountTolerance()).isEqualByComparingTo("0.05");
    assertThat(properties.fundSellQuantityTolerance()).isEqualByComparingTo("0.002");
    assertThat(properties.nearMissMultiplier()).isEqualByComparingTo("3");
    assertThat(properties.executionPriceConsistencyTolerance()).isEqualByComparingTo("0.03");
  }
}
