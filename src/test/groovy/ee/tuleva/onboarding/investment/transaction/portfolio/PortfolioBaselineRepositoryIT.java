package ee.tuleva.onboarding.investment.transaction.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PortfolioBaselineRepositoryIT {

  @Autowired private PortfolioBaselineRepository baselineRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void saveAndFindByFundIsin_persistsBaselineWithEntries() {
    PortfolioBaseline baseline =
        PortfolioBaseline.builder()
            .fundIsin("EE3600109435")
            .baselineDate(LocalDate.of(2026, 4, 30))
            .loadedBy("ops")
            .build();
    baseline.addEntry(
        PortfolioBaselineEntry.builder()
            .instrumentIsin("IE00BFNM3G45")
            .quantity(new BigDecimal("100000.0000"))
            .avgUnitCost(new BigDecimal("10.00000000"))
            .build());
    baseline.addEntry(
        PortfolioBaselineEntry.builder()
            .instrumentIsin("IE0009FT4LX4")
            .quantity(new BigDecimal("50000.0000"))
            .avgUnitCost(new BigDecimal("4.50000000"))
            .build());

    baselineRepository.save(baseline);
    entityManager.flush();
    entityManager.clear();

    PortfolioBaseline loaded = baselineRepository.findByFundIsin("EE3600109435").orElseThrow();

    assertThat(loaded.getBaselineDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    assertThat(loaded.getLoadedAt()).isNotNull();
    assertThat(loaded.getLoadedBy()).isEqualTo("ops");
    assertThat(loaded.getEntries()).hasSize(2);
  }

  @Test
  void findByFundIsin_returnsEmptyWhenMissing() {
    assertThat(baselineRepository.findByFundIsin("NOPE")).isEmpty();
  }
}
