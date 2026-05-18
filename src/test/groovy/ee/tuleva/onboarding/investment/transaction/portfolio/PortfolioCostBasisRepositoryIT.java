package ee.tuleva.onboarding.investment.transaction.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class PortfolioCostBasisRepositoryIT {

  private static final String FUND_ISIN = "EE3600109435";
  private static final String INSTRUMENT_ISIN = "IE00BFNM3G45";

  @Autowired private PortfolioCostBasisRepository repository;
  @Autowired private EntityManager entityManager;

  @Test
  void uniqueConstraint_preventsDuplicateRowsForSameKey() {
    repository.save(row(LocalDate.of(2026, 5, 1), "100.0000", "10.00000000", "1000.00"));
    entityManager.flush();

    assertThatThrownBy(
            () -> {
              repository.save(row(LocalDate.of(2026, 5, 1), "200.0000", "11.00000000", "2200.00"));
              entityManager.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findLatestByFundIsinAndInstrumentIsinBefore_returnsMostRecentPriorRow() {
    repository.save(row(LocalDate.of(2026, 5, 1), "100.0000", "10.00000000", "1000.00"));
    repository.save(row(LocalDate.of(2026, 5, 2), "150.0000", "10.50000000", "1575.00"));
    repository.save(row(LocalDate.of(2026, 5, 4), "200.0000", "11.00000000", "2200.00"));
    entityManager.flush();

    PortfolioCostBasis latest =
        repository
            .findLatestByFundIsinAndInstrumentIsinBefore(
                FUND_ISIN, INSTRUMENT_ISIN, LocalDate.of(2026, 5, 4))
            .orElseThrow();

    assertThat(latest.getAsOfDate()).isEqualTo(LocalDate.of(2026, 5, 2));
  }

  @Test
  void findLatestByFundIsinAndInstrumentIsinBefore_returnsEmptyWhenNoPriorRow() {
    assertThat(
            repository.findLatestByFundIsinAndInstrumentIsinBefore(
                FUND_ISIN, INSTRUMENT_ISIN, LocalDate.of(2026, 5, 1)))
        .isEmpty();
  }

  @Test
  void findByFundIsinAndAsOfDate_returnsAllInstrumentRowsForFundOnDate() {
    repository.save(row(LocalDate.of(2026, 5, 1), "100.0000", "10.00000000", "1000.00"));
    PortfolioCostBasis other =
        PortfolioCostBasis.builder()
            .fundIsin(FUND_ISIN)
            .instrumentIsin("IE0009FT4LX4")
            .asOfDate(LocalDate.of(2026, 5, 1))
            .quantity(new BigDecimal("50.0000"))
            .avgUnitCost(new BigDecimal("4.50000000"))
            .totalCost(new BigDecimal("225.00"))
            .deltaQuantity(new BigDecimal("50.0000"))
            .source("DERIVED")
            .build();
    repository.save(other);
    entityManager.flush();

    assertThat(repository.findByFundIsinAndAsOfDate(FUND_ISIN, LocalDate.of(2026, 5, 1)))
        .hasSize(2);
  }

  private PortfolioCostBasis row(LocalDate asOfDate, String qty, String avg, String totalCost) {
    return PortfolioCostBasis.builder()
        .fundIsin(FUND_ISIN)
        .instrumentIsin(INSTRUMENT_ISIN)
        .asOfDate(asOfDate)
        .quantity(new BigDecimal(qty))
        .avgUnitCost(new BigDecimal(avg))
        .totalCost(new BigDecimal(totalCost))
        .deltaQuantity(new BigDecimal(qty))
        .source("DERIVED")
        .build();
  }
}
