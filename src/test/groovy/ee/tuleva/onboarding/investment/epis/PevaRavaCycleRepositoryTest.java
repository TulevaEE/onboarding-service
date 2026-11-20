package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.IGNORE;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PevaRavaCycleRepositoryTest {

  @Autowired private PevaRavaCycleRepository repository;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void save_persistsCycleAndSetsTimestamps() {
    PevaRavaCycleEntity saved =
        repository.save(
            PevaRavaCycleEntity.builder()
                .lockDate(LocalDate.of(2026, 3, 31))
                .execDate(LocalDate.of(2026, 5, 1))
                .phase(IGNORE)
                .build());

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(repository.findById(saved.getId())).contains(saved);
  }

  @Test
  void findByExecDate_returnsMatchingCycle() {
    PevaRavaCycleEntity cycle =
        repository.save(
            PevaRavaCycleEntity.builder()
                .lockDate(LocalDate.of(2026, 3, 31))
                .execDate(LocalDate.of(2026, 5, 1))
                .phase(ACTIVE)
                .build());

    Optional<PevaRavaCycleEntity> found = repository.findByExecDate(LocalDate.of(2026, 5, 1));

    assertThat(found).contains(cycle);
  }

  @Test
  void findReportsByExecDate_readsTheLinksFromTheDatabaseNotFromAnAlreadyLoadedCycle() {
    repository.save(
        PevaRavaCycleEntity.builder()
            .lockDate(LocalDate.of(2026, 3, 31))
            .execDate(LocalDate.of(2026, 5, 1))
            .phase(ACTIVE)
            .build());
    Long r17ReportId = report(R17_PEVA);
    entityManager
        .createNativeQuery(
            "UPDATE peva_rava_cycle SET r17_report_id = :r17 WHERE exec_date = :exec")
        .setParameter("r17", r17ReportId)
        .setParameter("exec", LocalDate.of(2026, 5, 1))
        .executeUpdate();

    assertThat(repository.findReportsByExecDate(LocalDate.of(2026, 5, 1)))
        .contains(new PevaRavaCycleReports(r17ReportId, null));
    assertThat(repository.findReportsByExecDate(LocalDate.of(2026, 9, 1))).isEmpty();
  }

  @Test
  void findCurrentCycle_returnsEarliestCycleWithExecDateOnOrAfterGivenDate() {
    repository.save(
        PevaRavaCycleEntity.builder()
            .lockDate(LocalDate.of(2025, 11, 30))
            .execDate(LocalDate.of(2026, 1, 1))
            .phase(IGNORE)
            .build());
    PevaRavaCycleEntity current =
        repository.save(
            PevaRavaCycleEntity.builder()
                .lockDate(LocalDate.of(2026, 3, 31))
                .execDate(LocalDate.of(2026, 5, 1))
                .phase(IGNORE)
                .build());
    repository.save(
        PevaRavaCycleEntity.builder()
            .lockDate(LocalDate.of(2026, 7, 31))
            .execDate(LocalDate.of(2026, 9, 1))
            .phase(IGNORE)
            .build());

    Optional<PevaRavaCycleEntity> found =
        repository.findTopByExecDateGreaterThanEqualOrderByExecDateAsc(LocalDate.of(2026, 4, 15));

    assertThat(found).contains(current);
  }

  private Long report(ReportType reportType) {
    return reportRepository
        .save(
            InvestmentReport.builder()
                .provider(EPIS)
                .reportType(reportType)
                .reportDate(LocalDate.of(2026, 4, 2))
                .createdAt(Instant.now())
                .build())
        .getId();
  }
}
