package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.config.CacheConfiguration.FUND_TABLE_CACHE;
import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.config.CacheConfiguration;
import ee.tuleva.onboarding.fund.manager.FundManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(CacheConfiguration.class)
@TestPropertySource(properties = "spring.cache.type=simple")
class FundRepositoryCachingTest {

  @Autowired TestEntityManager entityManager;
  @Autowired FundRepository fundRepository;

  @Autowired
  @Qualifier("ttlCacheManager")
  CacheManager ttlCacheManager;

  @Test
  void findByIsinServesRepeatedCallsFromCache() {
    persistFund("EE0000000001");

    Fund first = fundRepository.findByIsin("EE0000000001");
    entityManager.getEntityManager().clear();
    Fund second = fundRepository.findByIsin("EE0000000001");

    assertThat(second).isSameAs(first);
  }

  @Test
  void findAllServesRepeatedCallsFromCache() {
    persistFund("EE0000000002");

    Iterable<Fund> first = fundRepository.findAll();
    entityManager.getEntityManager().clear();
    Iterable<Fund> second = fundRepository.findAll();

    assertThat(second).isSameAs(first);
  }

  @Test
  void saveEvictsTheFundCache() {
    persistFund("EE0000000003");
    Fund cached = fundRepository.findByIsin("EE0000000003");

    fundRepository.save(cached);
    entityManager.getEntityManager().clear();
    Fund afterSave = fundRepository.findByIsin("EE0000000003");

    assertThat(afterSave).isNotSameAs(cached);
  }

  @Test
  void saveEvictsTheFundCacheEvenWhenTheSaveFails() {
    persistFund("EE0000000005");
    Fund cached = fundRepository.findByIsin("EE0000000005");

    assertThatThrownBy(() -> fundRepository.save(null))
        .isInstanceOf(InvalidDataAccessApiUsageException.class);
    entityManager.getEntityManager().clear();
    Fund afterFailedSave = fundRepository.findByIsin("EE0000000005");

    assertThat(afterFailedSave).isNotSameAs(cached);
  }

  @Test
  void doesNotCacheMissesForUnknownIsins() {
    fundRepository.findByIsin("EE0000000404");

    assertThat(ttlCacheManager.getCache(FUND_TABLE_CACHE).get("byIsin:EE0000000404")).isNull();
  }

  private void persistFund(String isin) {
    entityManager.persist(
        Fund.builder()
            .isin(isin)
            .nameEstonian("Tuleva Maailma Aktsiate Pensionifond")
            .nameEnglish("Tuleva World Stocks Pension Fund")
            .shortName("TUK75")
            .pillar(2)
            .equityShare(BigDecimal.ZERO)
            .managementFeeRate(new BigDecimal("0.0034"))
            .ongoingChargesFigure(new BigDecimal("0.005"))
            .status(ACTIVE)
            .fundManager(FundManager.builder().id(1L).name("Tuleva").build())
            .inceptionDate(LocalDate.parse("2019-01-01"))
            .build());
    entityManager.flush();
  }
}
