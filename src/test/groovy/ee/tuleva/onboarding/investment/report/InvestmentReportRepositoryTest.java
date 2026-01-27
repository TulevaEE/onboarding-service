package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvestmentReportRepositoryTest {

  @Autowired private InvestmentReportRepository repository;

  @Test
  void save_persistsJsonbFields() {
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(LocalDate.of(2026, 1, 15))
            .rawData(List.of(Map.of("col1", "val1", "col2", 123)))
            .metadata(Map.of("filename", "test.csv"))
            .createdAt(Instant.now())
            .build();

    InvestmentReport saved = repository.save(report);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getRawData()).hasSize(1);
    assertThat(saved.getRawData().getFirst().get("col1")).isEqualTo("val1");
    assertThat(saved.getMetadata().get("filename")).isEqualTo("test.csv");
  }

  @Test
  void existsByProviderAndReportTypeAndReportDate_returnsTrueWhenExists() {
    repository.save(
        InvestmentReport.builder()
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(LocalDate.of(2026, 1, 15))
            .createdAt(Instant.now())
            .build());

    boolean exists =
        repository.existsByProviderAndReportTypeAndReportDate(
            SWEDBANK, POSITIONS, LocalDate.of(2026, 1, 15));

    assertThat(exists).isTrue();
  }

  @Test
  void existsByProviderAndReportTypeAndReportDate_returnsFalseWhenNotExists() {
    boolean exists =
        repository.existsByProviderAndReportTypeAndReportDate(
            SWEDBANK, POSITIONS, LocalDate.of(2026, 1, 15));

    assertThat(exists).isFalse();
  }

  @Test
  void findByProviderAndReportTypeAndReportDate_returnsReport() {
    repository.save(
        InvestmentReport.builder()
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(LocalDate.of(2026, 1, 15))
            .rawData(List.of(Map.of("test", "data")))
            .createdAt(Instant.now())
            .build());

    Optional<InvestmentReport> result =
        repository.findByProviderAndReportTypeAndReportDate(
            SWEDBANK, POSITIONS, LocalDate.of(2026, 1, 15));

    assertThat(result).isPresent();
    assertThat(result.get().getRawData().getFirst().get("test")).isEqualTo("data");
  }

  @Test
  void findByProviderAndReportTypeAndReportDate_distinguishesByReportType() {
    repository.save(
        InvestmentReport.builder()
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(LocalDate.of(2026, 1, 15))
            .createdAt(Instant.now())
            .build());

    Optional<InvestmentReport> result =
        repository.findByProviderAndReportTypeAndReportDate(
            SWEDBANK, PENDING_TRANSACTIONS, LocalDate.of(2026, 1, 15));

    assertThat(result).isEmpty();
  }
}
