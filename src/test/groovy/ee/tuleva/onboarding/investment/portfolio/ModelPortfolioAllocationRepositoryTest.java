package ee.tuleva.onboarding.investment.portfolio;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.portfolio.Provider.BNP_PARIBAS;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.portfolio.Provider.XTRACKERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class ModelPortfolioAllocationRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private ModelPortfolioAllocationRepository repository;

  @Test
  void saveAndRetrieve_withRealData() {
    var allocation =
        ModelPortfolioAllocation.builder()
            .effectiveDate(LocalDate.of(2025, 12, 1))
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .ticker("XRSM.DE")
            .weight(new BigDecimal("0.174"))
            .label("Xtrackers MSCI USA Screened UCITS ETF")
            .provider(XTRACKERS)
            .build();

    entityManager.persistAndFlush(allocation);
    entityManager.clear();

    var retrieved = repository.findById(allocation.getId()).orElseThrow();

    assertThat(retrieved.getEffectiveDate()).isEqualTo(LocalDate.of(2025, 12, 1));
    assertThat(retrieved.getFund()).isEqualTo(TUK75);
    assertThat(retrieved.getIsin()).isEqualTo("IE00BJZ2DC62");
    assertThat(retrieved.getTicker()).isEqualTo("XRSM.DE");
    assertThat(retrieved.getWeight()).isEqualByComparingTo("0.174");
    assertThat(retrieved.getLabel()).isEqualTo("Xtrackers MSCI USA Screened UCITS ETF");
    assertThat(retrieved.getProvider()).isEqualTo(XTRACKERS);
    assertThat(retrieved.getCreatedAt()).isNotNull();
  }

  @Test
  void findByFundAndEffectiveDate_multiplePositions() {
    var date = LocalDate.of(2025, 12, 1);

    var xtrackers =
        ModelPortfolioAllocation.builder()
            .effectiveDate(date)
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .ticker("XRSM.DE")
            .weight(new BigDecimal("0.174"))
            .label("Xtrackers MSCI USA Screened UCITS ETF")
            .provider(XTRACKERS)
            .build();

    var bnp =
        ModelPortfolioAllocation.builder()
            .effectiveDate(date)
            .fund(TUK75)
            .isin("LU1291099718")
            .ticker("EEUX.DE")
            .weight(new BigDecimal("0.125"))
            .label("BNP Paribas Easy MSCI EUROPE MIN TE UCITS ETF")
            .provider(BNP_PARIBAS)
            .build();

    var otherFund =
        ModelPortfolioAllocation.builder()
            .effectiveDate(date)
            .fund(TUK00)
            .isin("IE00BFG1TM61")
            .weight(new BigDecimal("0.2954"))
            .provider(ISHARES)
            .build();

    entityManager.persist(xtrackers);
    entityManager.persist(bnp);
    entityManager.persist(otherFund);
    entityManager.flush();

    var result = repository.findByFundAndEffectiveDate(TUK75, date);

    assertThat(result).hasSize(2);
    assertThat(result).extracting("isin").containsExactlyInAnyOrder("IE00BJZ2DC62", "LU1291099718");
    assertThat(result).extracting("provider").containsExactlyInAnyOrder(XTRACKERS, BNP_PARIBAS);
  }

  @Test
  void findLatestByFund_returnsNewestEffectiveDate() {
    var olderDate = LocalDate.of(2025, 6, 30);
    var newerDate = LocalDate.of(2025, 12, 1);

    var olderAllocation =
        ModelPortfolioAllocation.builder()
            .effectiveDate(olderDate)
            .fund(TUK75)
            .isin("IE00B4L5Y983")
            .weight(new BigDecimal("0.25"))
            .provider(ISHARES)
            .build();

    var newerAllocation1 =
        ModelPortfolioAllocation.builder()
            .effectiveDate(newerDate)
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .weight(new BigDecimal("0.174"))
            .provider(XTRACKERS)
            .build();

    var newerAllocation2 =
        ModelPortfolioAllocation.builder()
            .effectiveDate(newerDate)
            .fund(TUK75)
            .isin("LU1291099718")
            .weight(new BigDecimal("0.125"))
            .provider(BNP_PARIBAS)
            .build();

    entityManager.persist(olderAllocation);
    entityManager.persist(newerAllocation1);
    entityManager.persist(newerAllocation2);
    entityManager.flush();

    var result = repository.findLatestByFund(TUK75);

    assertThat(result).hasSize(2);
    assertThat(result).extracting("effectiveDate").containsOnly(newerDate);
    assertThat(result).extracting("provider").containsExactlyInAnyOrder(XTRACKERS, BNP_PARIBAS);
  }

  @Test
  void findLatestByFund_returnsEmptyWhenNoData() {
    var result = repository.findLatestByFund(TUK75);

    assertThat(result).isEmpty();
  }
}
