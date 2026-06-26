package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class TransactionMatchingPolicyTest {

  @Autowired private InvestmentParameterRepository repository;

  private TransactionMatchingPolicy policyAt(Instant now) {
    return new TransactionMatchingPolicy(repository, Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void current_returnsSeededTolerances() {
    TransactionMatchingProperties properties =
        policyAt(Instant.parse("2026-06-12T07:00:00Z")).current();

    assertThat(properties.etfQuantityTolerance()).isEqualByComparingTo("0.0001");
    assertThat(properties.fundBuyAmountTolerance()).isEqualByComparingTo("0.02");
    assertThat(properties.fundSellQuantityTolerance()).isEqualByComparingTo("0.0001");
    assertThat(properties.nearMissMultiplier()).isEqualByComparingTo("5");
  }

  @Test
  void current_beforeAnyEffectiveParameter_throws() {
    TransactionMatchingPolicy policy = policyAt(Instant.parse("2026-05-31T07:00:00Z"));

    assertThatThrownBy(policy::current).isInstanceOf(IllegalStateException.class);
  }
}
