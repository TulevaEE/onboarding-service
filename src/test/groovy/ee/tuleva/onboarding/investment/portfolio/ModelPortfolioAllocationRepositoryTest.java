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
  void findLatestByFundAsOf_returnsAllocationsEffectiveOnOrBeforeAsOfDate() {
    var oldestDate = LocalDate.of(2025, 3, 1);
    var middleDate = LocalDate.of(2025, 6, 30);
    var newestDate = LocalDate.of(2025, 12, 1);

    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(oldestDate)
            .fund(TUK75)
            .isin("IE00OLDEST")
            .weight(new BigDecimal("0.30"))
            .provider(ISHARES)
            .build());
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(middleDate)
            .fund(TUK75)
            .isin("IE00MIDDLE")
            .weight(new BigDecimal("0.25"))
            .provider(ISHARES)
            .build());
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(newestDate)
            .fund(TUK75)
            .isin("IE00NEWEST")
            .weight(new BigDecimal("0.174"))
            .provider(XTRACKERS)
            .build());
    entityManager.flush();

    var beforeAll = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 2, 1));
    var afterOldest = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 8, 1));
    var afterNewest = repository.findLatestByFundAsOf(TUK75, LocalDate.of(2025, 12, 1));

    assertThat(beforeAll).isEmpty();
    assertThat(afterOldest).hasSize(1);
    assertThat(afterOldest).extracting("effectiveDate").containsOnly(middleDate);
    assertThat(afterNewest).hasSize(1);
    assertThat(afterNewest).extracting("effectiveDate").containsOnly(newestDate);
  }

  @Test
  void findFirstByIsinAsOf_returnsLatestAllocationWithProviderOnOrBeforeDate() {
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(LocalDate.of(2025, 3, 1))
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .weight(new BigDecimal("0.30"))
            .provider(ISHARES)
            .build());
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(LocalDate.of(2025, 12, 1))
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .weight(new BigDecimal("0.174"))
            .provider(XTRACKERS)
            .build());
    // Future-dated allocation that must NOT affect resolution for an earlier as-of date.
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(LocalDate.of(2026, 3, 1))
            .fund(TUK75)
            .isin("IE00BJZ2DC62")
            .weight(new BigDecimal("0.20"))
            .provider(ISHARES)
            .build());
    entityManager.flush();

    var asOfMidPeriod =
        repository
            .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                "IE00BJZ2DC62", LocalDate.of(2026, 1, 15));
    var asOfAfterFuture =
        repository
            .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                "IE00BJZ2DC62", LocalDate.of(2026, 6, 1));
    var asOfBeforeAny =
        repository
            .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                "IE00BJZ2DC62", LocalDate.of(2025, 1, 1));
    var unknownIsin =
        repository
            .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                "XX0000000000", LocalDate.of(2026, 1, 15));

    // As of 2026-01-15 the future 2026-03-01 allocation is excluded; latest effective is
    // 2025-12-01.
    assertThat(asOfMidPeriod.orElseThrow().getProvider()).isEqualTo(XTRACKERS);
    assertThat(asOfMidPeriod.orElseThrow().getEffectiveDate()).isEqualTo(LocalDate.of(2025, 12, 1));
    // As of 2026-06-01 the future allocation is now in scope and wins.
    assertThat(asOfAfterFuture.orElseThrow().getEffectiveDate())
        .isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(asOfBeforeAny).isEmpty();
    assertThat(unknownIsin).isEmpty();
  }

  @Test
  void findPreviousByFundAsOf_returnsSecondToLatestAsOf() {
    var oldestDate = LocalDate.of(2025, 3, 1);
    var middleDate = LocalDate.of(2025, 6, 30);
    var newestDate = LocalDate.of(2025, 12, 1);

    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(oldestDate)
            .fund(TUK75)
            .isin("IE00OLDEST")
            .weight(new BigDecimal("0.30"))
            .provider(ISHARES)
            .build());
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(middleDate)
            .fund(TUK75)
            .isin("IE00MIDDLE")
            .weight(new BigDecimal("0.25"))
            .provider(ISHARES)
            .build());
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(newestDate)
            .fund(TUK75)
            .isin("IE00NEWEST")
            .weight(new BigDecimal("0.174"))
            .provider(XTRACKERS)
            .build());
    entityManager.flush();

    var asOfNewest = repository.findPreviousByFundAsOf(TUK75, newestDate);
    var asOfMiddle = repository.findPreviousByFundAsOf(TUK75, LocalDate.of(2025, 8, 1));
    var asOfOldest = repository.findPreviousByFundAsOf(TUK75, LocalDate.of(2025, 4, 1));

    assertThat(asOfNewest).hasSize(1);
    assertThat(asOfNewest).extracting("effectiveDate").containsOnly(middleDate);
    assertThat(asOfMiddle).hasSize(1);
    assertThat(asOfMiddle).extracting("effectiveDate").containsOnly(oldestDate);
    assertThat(asOfOldest).isEmpty();
  }

  @Test
  void findFutureEffectiveDates_returnsDistinctUpcomingVersionDatesInOrder() {
    var asOf = LocalDate.of(2025, 6, 1);
    var current = LocalDate.of(2025, 3, 1);
    var nextSwitch = LocalDate.of(2025, 9, 1);
    var laterSwitch = LocalDate.of(2025, 12, 1);

    // Current version (on or before asOf) — must be excluded.
    entityManager.persist(allocationOn(current, "IE00CURRENT"));
    // Two ISINs share the first future version's date — date must appear once (DISTINCT).
    entityManager.persist(allocationOn(nextSwitch, "IE00NEXT1"));
    entityManager.persist(allocationOn(nextSwitch, "IE00NEXT2"));
    entityManager.persist(allocationOn(laterSwitch, "IE00LATER"));
    // A future version for another fund — must be excluded.
    entityManager.persist(
        ModelPortfolioAllocation.builder()
            .effectiveDate(laterSwitch)
            .fund(TUK00)
            .isin("IE00OTHERFUND")
            .weight(new BigDecimal("1.0"))
            .provider(ISHARES)
            .build());
    entityManager.flush();

    var future = repository.findFutureEffectiveDates(TUK75, asOf);

    assertThat(future).containsExactly(nextSwitch, laterSwitch);
  }

  private ModelPortfolioAllocation allocationOn(LocalDate date, String isin) {
    return ModelPortfolioAllocation.builder()
        .effectiveDate(date)
        .fund(TUK75)
        .isin(isin)
        .weight(new BigDecimal("1.0"))
        .provider(ISHARES)
        .build();
  }
}
