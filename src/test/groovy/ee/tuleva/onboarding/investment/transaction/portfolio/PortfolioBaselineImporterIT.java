package ee.tuleva.onboarding.investment.transaction.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioBaselineImporterIT {

  @Autowired private PortfolioBaselineImporter importer;
  @Autowired private PortfolioBaselineRepository baselineRepository;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void clean() {
    baselineRepository.deleteAll();
    entityManager.flush();
  }

  @Test
  void importCsv_persistsOneBaselinePerFundWithEntries() {
    String csv =
        """
        fund_isin,instrument_isin,quantity,avg_unit_cost,baseline_date
        EE3600109435,IE00BFNM3G45,100000.0000,10.00000000,2026-04-30
        EE3600109435,IE0009FT4LX4,50000.0000,4.50000000,2026-04-30
        EE3600109443,LU0826455353,80000.0000,8.10000000,2026-04-30
        """;

    var baselines = importer.importCsv(toStream(csv), "ops");
    entityManager.flush();
    entityManager.clear();

    assertThat(baselines).hasSize(2);

    var tuk75 = baselineRepository.findByFundIsin("EE3600109435").orElseThrow();
    assertThat(tuk75.getBaselineDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    assertThat(tuk75.getLoadedBy()).isEqualTo("ops");
    assertThat(tuk75.getEntries()).hasSize(2);

    var tuk00 = baselineRepository.findByFundIsin("EE3600109443").orElseThrow();
    assertThat(tuk00.getEntries()).hasSize(1);
  }

  @Test
  void importCsv_replacesExistingBaselineForSameFund() {
    String firstCsv =
        """
        fund_isin,instrument_isin,quantity,avg_unit_cost,baseline_date
        EE3600109435,IE00BFNM3G45,100000.0000,10.00000000,2026-04-30
        """;
    importer.importCsv(toStream(firstCsv), "ops");

    String secondCsv =
        """
        fund_isin,instrument_isin,quantity,avg_unit_cost,baseline_date
        EE3600109435,IE0009FT4LX4,500.0000,5.00000000,2026-05-15
        EE3600109435,IE00BFNM3G45,200000.0000,11.00000000,2026-05-15
        """;
    importer.importCsv(toStream(secondCsv), "ops2");
    entityManager.flush();
    entityManager.clear();

    var baselines = baselineRepository.findAll();
    assertThat(baselines).hasSize(1);
    var loaded = baselines.get(0);
    assertThat(loaded.getBaselineDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    assertThat(loaded.getEntries()).hasSize(2);
  }

  @Test
  void importCsv_rejectsCsvMissingRequiredColumn() {
    String csv =
        """
        fund_isin,instrument_isin,quantity,baseline_date
        EE3600109435,IE00BFNM3G45,100000.0000,2026-04-30
        """;

    assertThatThrownBy(() -> importer.importCsv(toStream(csv), "ops"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("avg_unit_cost");
  }

  @Test
  void importCsv_rejectsMixedBaselineDateForSameFund() {
    String csv =
        """
        fund_isin,instrument_isin,quantity,avg_unit_cost,baseline_date
        EE3600109435,IE00BFNM3G45,100000.0000,10.00000000,2026-04-30
        EE3600109435,IE0009FT4LX4,50000.0000,4.50000000,2026-05-01
        """;

    assertThatThrownBy(() -> importer.importCsv(toStream(csv), "ops"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Mixed baseline_date");
  }

  private static ByteArrayInputStream toStream(String s) {
    return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
  }
}
