package ee.tuleva.onboarding.investment.portfolio;

import static ee.tuleva.onboarding.investment.portfolio.Provider.BNP_PARIBAS;
import static ee.tuleva.onboarding.investment.portfolio.Provider.XTRACKERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class ProviderLimitRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ProviderLimitRepository repository;

  @Test
  void saveAndRetrieve() {
    var limit =
        ProviderLimit.builder()
            .effectiveDate(LocalDate.of(2025, 11, 7))
            .fundCode("tkt100")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    entityManager.persistAndFlush(limit);
    entityManager.clear();

    var retrieved = repository.findById(limit.getId()).orElseThrow();

    assertThat(retrieved.getEffectiveDate()).isEqualTo(LocalDate.of(2025, 11, 7));
    assertThat(retrieved.getFundCode()).isEqualTo("tkt100");
    assertThat(retrieved.getProvider()).isEqualTo(XTRACKERS);
    assertThat(retrieved.getSoftLimitPercent()).isEqualByComparingTo("0.1965");
    assertThat(retrieved.getHardLimitPercent()).isEqualByComparingTo("0.20");
    assertThat(retrieved.getCreatedAt()).isNotNull();
  }

  @Test
  void findByFundCodeAndEffectiveDate() {
    var date = LocalDate.of(2025, 11, 7);

    var xtrackersLimit =
        ProviderLimit.builder()
            .effectiveDate(date)
            .fundCode("tkt100")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    var bnpLimit =
        ProviderLimit.builder()
            .effectiveDate(date)
            .fundCode("tkt100")
            .provider(BNP_PARIBAS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    var otherFund =
        ProviderLimit.builder()
            .effectiveDate(date)
            .fundCode("tuk75")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.15"))
            .hardLimitPercent(new BigDecimal("0.18"))
            .build();

    entityManager.persist(xtrackersLimit);
    entityManager.persist(bnpLimit);
    entityManager.persist(otherFund);
    entityManager.flush();

    var result = repository.findByFundCodeAndEffectiveDate("tkt100", date);

    assertThat(result).hasSize(2);
    assertThat(result).extracting("provider").containsExactlyInAnyOrder(XTRACKERS, BNP_PARIBAS);
  }

  @Test
  void findByFundCodeAndEffectiveDateAndProvider() {
    var date = LocalDate.of(2025, 11, 7);

    var xtrackersLimit =
        ProviderLimit.builder()
            .effectiveDate(date)
            .fundCode("tkt100")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    var bnpLimit =
        ProviderLimit.builder()
            .effectiveDate(date)
            .fundCode("tkt100")
            .provider(BNP_PARIBAS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    entityManager.persist(xtrackersLimit);
    entityManager.persist(bnpLimit);
    entityManager.flush();

    var result = repository.findByFundCodeAndEffectiveDateAndProvider("tkt100", date, XTRACKERS);

    assertThat(result).isPresent();
    assertThat(result.get().getProvider()).isEqualTo(XTRACKERS);
  }

  @Test
  void findLatestByFundCode() {
    var olderDate = LocalDate.of(2025, 1, 1);
    var newerDate = LocalDate.of(2025, 11, 7);

    var olderLimit =
        ProviderLimit.builder()
            .effectiveDate(olderDate)
            .fundCode("tkt100")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.15"))
            .hardLimitPercent(new BigDecimal("0.18"))
            .build();

    var newerXtrackers =
        ProviderLimit.builder()
            .effectiveDate(newerDate)
            .fundCode("tkt100")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    var newerBnp =
        ProviderLimit.builder()
            .effectiveDate(newerDate)
            .fundCode("tkt100")
            .provider(BNP_PARIBAS)
            .softLimitPercent(new BigDecimal("0.1965"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    entityManager.persist(olderLimit);
    entityManager.persist(newerXtrackers);
    entityManager.persist(newerBnp);
    entityManager.flush();

    var result = repository.findLatestByFundCode("tkt100");

    assertThat(result).hasSize(2);
    assertThat(result).extracting("effectiveDate").containsOnly(newerDate);
    assertThat(result).extracting("provider").containsExactlyInAnyOrder(XTRACKERS, BNP_PARIBAS);
  }

  @Test
  void findLatestByFundCode_returnsEmptyWhenNoData() {
    var result = repository.findLatestByFundCode("nonexistent");

    assertThat(result).isEmpty();
  }
}
