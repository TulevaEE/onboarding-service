package ee.tuleva.onboarding.investment.portfolio;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.portfolio.Provider.BNP_PARIBAS;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.portfolio.Provider.XTRACKERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class PositionLimitRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private PositionLimitRepository repository;

  @Test
  void saveAndRetrieve_withRealData() {
    var limit =
        PositionLimit.builder()
            .effectiveDate(LocalDate.of(2025, 11, 7))
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .ticker("XRSM.DE")
            .label("Xtrackers MSCI USA Screened UCITS ETF")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.1862"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    entityManager.persistAndFlush(limit);
    entityManager.clear();

    var retrieved = repository.findById(limit.getId()).orElseThrow();

    assertThat(retrieved.getEffectiveDate()).isEqualTo(LocalDate.of(2025, 11, 7));
    assertThat(retrieved.getFund()).isEqualTo(TUK75);
    assertThat(retrieved.getIsin()).isEqualTo("IE00BJZ2DC62");
    assertThat(retrieved.getTicker()).isEqualTo("XRSM.DE");
    assertThat(retrieved.getLabel()).isEqualTo("Xtrackers MSCI USA Screened UCITS ETF");
    assertThat(retrieved.getProvider()).isEqualTo(XTRACKERS);
    assertThat(retrieved.getSoftLimitPercent()).isEqualByComparingTo("0.1862");
    assertThat(retrieved.getHardLimitPercent()).isEqualByComparingTo("0.20");
    assertThat(retrieved.getCreatedAt()).isNotNull();
  }

  @Test
  void findByFundAndEffectiveDate_multiplePositions() {
    var date = LocalDate.of(2025, 11, 7);

    var xtrackers =
        PositionLimit.builder()
            .effectiveDate(date)
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .ticker("XRSM.DE")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.1862"))
            .hardLimitPercent(new BigDecimal("0.20"))
            .build();

    var bnp =
        PositionLimit.builder()
            .effectiveDate(date)
            .fund(TUK75)
            .isin("LU1291099718")
            .ticker("EEUX.DE")
            .provider(BNP_PARIBAS)
            .softLimitPercent(new BigDecimal("0.1338"))
            .hardLimitPercent(new BigDecimal("0.1438"))
            .build();

    var otherFund =
        PositionLimit.builder()
            .effectiveDate(date)
            .fund(TUK00)
            .isin("IE00BFG1TM61")
            .provider(ISHARES)
            .softLimitPercent(new BigDecimal("0.2965"))
            .hardLimitPercent(new BigDecimal("0.30"))
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
  void findLatestByFundAsOf_returnsLimitsEffectiveOnOrBeforeAsOfDate() {
    var olderDate = LocalDate.of(2025, 6, 30);
    var newerDate = LocalDate.of(2026, 3, 30);

    var older =
        PositionLimit.builder()
            .effectiveDate(olderDate)
            .fund(TUK75)
            .isin("IE00BKPTWY98")
            .provider(ISHARES)
            .softLimitPercent(new BigDecimal("0.1070"))
            .hardLimitPercent(new BigDecimal("0.1150"))
            .build();

    var newer =
        PositionLimit.builder()
            .effectiveDate(newerDate)
            .fund(TUK75)
            .isin("IE00BKPTWY98")
            .provider(ISHARES)
            .softLimitPercent(new BigDecimal("0.1241"))
            .hardLimitPercent(new BigDecimal("0.30"))
            .build();

    entityManager.persist(older);
    entityManager.persist(newer);
    entityManager.flush();

    var beforeBoth = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 6, 29));
    var afterOlder = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 11, 7));
    var afterNewer = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2026, 4, 1));

    assertThat(beforeBoth).isEmpty();
    assertThat(afterOlder).hasSize(1);
    assertThat(afterOlder).extracting("effectiveDate").containsOnly(olderDate);
    assertThat(afterOlder.getFirst().getSoftLimitPercent()).isEqualByComparingTo("0.1070");
    assertThat(afterNewer).hasSize(1);
    assertThat(afterNewer).extracting("effectiveDate").containsOnly(newerDate);
  }

  @Test
  void findLatestByFundAsOf_resolvesPerIsinEffectiveDate() {
    var originalDate = LocalDate.of(2025, 6, 30);
    var updatedDate = LocalDate.of(2025, 11, 7);

    entityManager.persist(
        PositionLimit.builder()
            .effectiveDate(originalDate)
            .fund(TUK75)
            .isin("IE00UNCHANGED")
            .provider(ISHARES)
            .softLimitPercent(new BigDecimal("0.10"))
            .hardLimitPercent(new BigDecimal("0.12"))
            .build());
    entityManager.persist(
        PositionLimit.builder()
            .effectiveDate(updatedDate)
            .fund(TUK75)
            .isin("IE00NEW")
            .provider(XTRACKERS)
            .softLimitPercent(new BigDecimal("0.15"))
            .hardLimitPercent(new BigDecimal("0.18"))
            .build());
    entityManager.flush();

    var beforeBoth = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 6, 29));
    var afterFirst = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 8, 1));
    var afterBoth = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 12, 1));

    assertThat(beforeBoth).isEmpty();
    assertThat(afterFirst).hasSize(1);
    assertThat(afterFirst.getFirst().getIsin()).isEqualTo("IE00UNCHANGED");
    assertThat(afterBoth).hasSize(2);
    assertThat(afterBoth).extracting("isin").containsExactlyInAnyOrder("IE00UNCHANGED", "IE00NEW");
  }
}
