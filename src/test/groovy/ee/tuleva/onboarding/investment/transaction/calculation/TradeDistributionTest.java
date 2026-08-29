package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class TradeDistributionTest {

  @Test
  void distributeAmountWithThreshold_eliminatesSmallestOneAtATime() {
    var scores =
        List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
    var amount = new BigDecimal("15000");
    var threshold = new BigDecimal("5000");

    var result = TradeDistribution.distributeAmountWithThreshold(scores, amount, threshold);

    var nonZero = result.stream().filter(v -> v.compareTo(ZERO) > 0).toList();
    assertThat(nonZero).hasSize(3);
    nonZero.forEach(v -> assertThat(v).isGreaterThanOrEqualTo(new BigDecimal("4999")));
    assertThat(result.stream().reduce(ZERO, BigDecimal::add))
        .isCloseTo(amount, Offset.offset(BigDecimal.ONE));
  }
}
