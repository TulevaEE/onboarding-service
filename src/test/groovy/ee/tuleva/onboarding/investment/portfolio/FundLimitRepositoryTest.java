package ee.tuleva.onboarding.investment.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class FundLimitRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private FundLimitRepository repository;

  @Test
  void saveAndRetrieve_withRealData() {
    var limit =
        FundLimit.builder()
            .effectiveDate(LocalDate.of(2025, 11, 7))
            .fundCode("tkt100")
            .reserveSoft(new BigDecimal("500000.00"))
            .reserveHard(new BigDecimal("200000.00"))
            .minTransaction(new BigDecimal("50000.00"))
            .build();

    entityManager.persistAndFlush(limit);
    entityManager.clear();

    var retrieved = repository.findById(limit.getId()).orElseThrow();

    assertThat(retrieved.getEffectiveDate()).isEqualTo(LocalDate.of(2025, 11, 7));
    assertThat(retrieved.getFundCode()).isEqualTo("tkt100");
    assertThat(retrieved.getReserveSoft()).isEqualByComparingTo("500000.00");
    assertThat(retrieved.getReserveHard()).isEqualByComparingTo("200000.00");
    assertThat(retrieved.getMinTransaction()).isEqualByComparingTo("50000.00");
    assertThat(retrieved.getCreatedAt()).isNotNull();
  }

  @Test
  void saveAndRetrieve_withPartialFields() {
    var limit =
        FundLimit.builder()
            .effectiveDate(LocalDate.of(2025, 6, 30))
            .fundCode("tuk00")
            .reserveSoft(new BigDecimal("100000.00"))
            .reserveHard(new BigDecimal("200000.00"))
            .build();

    entityManager.persistAndFlush(limit);
    entityManager.clear();

    var retrieved = repository.findById(limit.getId()).orElseThrow();

    assertThat(retrieved.getReserveSoft()).isEqualByComparingTo("100000.00");
    assertThat(retrieved.getReserveHard()).isEqualByComparingTo("200000.00");
    assertThat(retrieved.getMinTransaction()).isNull();
  }

  @Test
  void findByFundCodeAndEffectiveDate_multipleFunds() {
    var date = LocalDate.of(2025, 11, 7);

    var tkt100 =
        FundLimit.builder()
            .effectiveDate(date)
            .fundCode("tkt100")
            .reserveSoft(new BigDecimal("500000.00"))
            .reserveHard(new BigDecimal("200000.00"))
            .minTransaction(new BigDecimal("50000.00"))
            .build();

    var tuk75 =
        FundLimit.builder()
            .effectiveDate(date)
            .fundCode("tuk75")
            .reserveSoft(new BigDecimal("200000.00"))
            .reserveHard(new BigDecimal("500000.00"))
            .minTransaction(new BigDecimal("50000.00"))
            .build();

    entityManager.persist(tkt100);
    entityManager.persist(tuk75);
    entityManager.flush();

    var result = repository.findByFundCodeAndEffectiveDate("tkt100", date);

    assertThat(result).isPresent();
    assertThat(result.get().getFundCode()).isEqualTo("tkt100");
    assertThat(result.get().getReserveSoft()).isEqualByComparingTo("500000.00");
  }

  @Test
  void findByFundCodeAndEffectiveDate_returnsEmptyWhenNotFound() {
    var result = repository.findByFundCodeAndEffectiveDate("nonexistent", LocalDate.now());

    assertThat(result).isEmpty();
  }

  @Test
  void findLatestByFundCode_returnsNewestEffectiveDate() {
    var olderDate = LocalDate.of(2025, 6, 30);
    var newerDate = LocalDate.of(2025, 11, 7);

    var olderLimit =
        FundLimit.builder()
            .effectiveDate(olderDate)
            .fundCode("tkt100")
            .reserveSoft(new BigDecimal("400000.00"))
            .minTransaction(new BigDecimal("40000.00"))
            .build();

    var newerLimit =
        FundLimit.builder()
            .effectiveDate(newerDate)
            .fundCode("tkt100")
            .reserveSoft(new BigDecimal("500000.00"))
            .reserveHard(new BigDecimal("200000.00"))
            .minTransaction(new BigDecimal("50000.00"))
            .build();

    entityManager.persist(olderLimit);
    entityManager.persist(newerLimit);
    entityManager.flush();

    var result = repository.findLatestByFundCode("tkt100");

    assertThat(result).isPresent();
    assertThat(result.get().getEffectiveDate()).isEqualTo(newerDate);
    assertThat(result.get().getReserveSoft()).isEqualByComparingTo("500000.00");
    assertThat(result.get().getReserveHard()).isEqualByComparingTo("200000.00");
    assertThat(result.get().getMinTransaction()).isEqualByComparingTo("50000.00");
  }

  @Test
  void findLatestByFundCode_returnsEmptyWhenNoData() {
    var result = repository.findLatestByFundCode("nonexistent");

    assertThat(result).isEmpty();
  }
}
